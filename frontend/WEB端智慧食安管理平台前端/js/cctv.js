/**
 * cctv.js — 视频监控管理模块
 *
 * 运行验证说明：
 * ① 点击左侧「视频监控管理」导航 → 默认展示「监控列表」Tab
 * ② 监控列表：按工位/在线状态/违规数筛选，违规数>0时标红，点击违规数跳转到「违规记录」Tab并自动筛选
 * ③ 点击「详情」→ 弹窗分3个只读Tab（设备信息/实时状态/预警配置）
 * ④ 点击「回放」→ 宽屏回放弹窗，可选日期+时间段+仅违规片段，片段列表点击可切换
 * ⑤ 切换「违规记录」Tab → 顶部统计卡、多维筛选列表，点击「查看片段」弹出违规片段小窗
 * ⑥ 点击「标记处理」可将未处理预警更新为已处理
 *
 * 核心改动点：
 * - 新增视频监控管理导航入口（sidebar.js + index.html）
 * - 新增 mockData.cctvCameras / cctvViolations 数据（mock-data.js）
 * - 新增监控列表筛选/分页/状态标签、违规数跳转联动
 * - 新增监控详情3-Tab只读弹窗
 * - 新增视频回放宽屏弹窗（检索区+播放区+片段列表）
 * - 新增违规记录Tab（统计卡+多维筛选+分页+片段小窗+处理操作）
 */

/* ============================================================
   常量
   ============================================================ */
const CAM_STATUS_MAP = {
    online:  { label: '在线', cls: 'tag-success' },
    offline: { label: '离线', cls: 'tag-danger'  }
};

const CAM_ZONE_MAP = {
    cutting: '切配区',
    cooking: '烹饪区',
    washing: '洗消区',
    storage: '仓储区'
};

const VIOL_EVENT_MAP = {
    no_mask:           '未戴口罩',
    no_gloves:         '未戴手套',
    no_handwash:       '未洗手',
    zone_violation:    '分区作业违规',
    raw_cooked_mix:    '生熟分离违规',
    cross_contamination: '交叉污染',
    fire_unattended:   '动火离人'
};

const VIOL_STATUS_MAP = {
    unhandled: { label: '未处理', cls: 'tag-danger'  },
    handled:   { label: '已处理', cls: 'tag-success' }
};

const ALERT_TYPE_LABELS = {
    no_mask:             '未戴口罩识别',
    no_gloves:           '未戴手套识别',
    no_handwash:         '未洗手识别',
    zone_violation:      '分区作业违规识别',
    raw_cooked_mix:      '生熟分离违规识别',
    cross_contamination: '交叉污染识别',
    fire_unattended:     '动火离人识别'
};

/* ============================================================
   全局状态
   ============================================================ */
window.cctvActiveTab   = 'camera';
window.camPage         = 1;
window.camPageSize     = 10;
window.camFilter       = { zone: '', status: '', violMin: '' };
window.violPage        = 1;
window.violPageSize    = 10;
window.violFilter      = { cameraId: '', zone: '', eventType: '', status: '', startDate: '', endDate: '', confidenceMin: '' };

/* ============================================================
   主入口
   ============================================================ */
function renderCctvPage(container) {
    window.cctvActiveTab = window.cctvActiveTab || 'camera';
    window.camPage  = 1;
    window.violPage = 1;

    container.innerHTML = `
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
            <div style="display:flex;gap:0;border:1px solid #dcdfe6;border-radius:4px;overflow:hidden;">
                <div onclick="cctvSwitchTab('camera')"
                     style="padding:8px 24px;cursor:pointer;font-size:14px;
                            background:${window.cctvActiveTab==='camera'?'#409eff':'#fff'};
                            color:${window.cctvActiveTab==='camera'?'#fff':'#606266'};
                            border:none;transition:all .2s;">
                    📷 监控列表
                </div>
                <div onclick="cctvSwitchTab('violation')"
                     style="padding:8px 24px;cursor:pointer;font-size:14px;
                            background:${window.cctvActiveTab==='violation'?'#409eff':'#fff'};
                            color:${window.cctvActiveTab==='violation'?'#fff':'#606266'};
                            border:none;border-left:1px solid #dcdfe6;transition:all .2s;">
                    ⚠️ 违规记录
                </div>
            </div>
            <span style="font-size:12px;color:#909399;">实时数据模拟 · 最后更新：${_cctvNowStr()}</span>
        </div>
        <div id="cctvTabContent"></div>`;

    _cctvRenderTab();
}

function cctvSwitchTab(tab) {
    window.cctvActiveTab = tab;
    renderCctvPage(document.getElementById('mainContent'));
}

function _cctvRenderTab() {
    const el = document.getElementById('cctvTabContent');
    if (!el) return;
    if (window.cctvActiveTab === 'camera') {
        _renderCameraTab(el);
    } else {
        _renderViolationTab(el);
    }
}

/* ============================================================
   监控列表 Tab
   ============================================================ */
function _renderCameraTab(container) {
    const f = window.camFilter;
    const zoneOpts = Object.entries(CAM_ZONE_MAP)
        .map(([v, l]) => `<option value="${v}" ${f.zone===v?'selected':''}>${l}</option>`).join('');

    container.innerHTML = `
        <div class="filter-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
            <select class="filter-input" style="width:130px;" onchange="window.camFilter.zone=this.value">
                <option value="" ${!f.zone?'selected':''}>全部工位</option>
                ${zoneOpts}
            </select>
            <select class="filter-input" style="width:120px;" onchange="window.camFilter.status=this.value">
                <option value="" ${!f.status?'selected':''}>全部状态</option>
                <option value="online"  ${f.status==='online' ?'selected':''}>在线</option>
                <option value="offline" ${f.status==='offline'?'selected':''}>离线</option>
            </select>
            <select class="filter-input" style="width:150px;" onchange="window.camFilter.violMin=this.value">
                <option value=""  ${f.violMin===''?'selected':''}>违规数不限</option>
                <option value="1" ${f.violMin==='1'?'selected':''}>今日违规≥1</option>
                <option value="5" ${f.violMin==='5'?'selected':''}>累计违规≥5</option>
                <option value="10"${f.violMin==='10'?'selected':''}>累计违规≥10</option>
            </select>
            <button class="btn btn-primary" onclick="camSearch()">🔍 查询</button>
            <button class="btn btn-default" onclick="camReset()">↺ 重置</button>
        </div>
        <div id="camTableWrap"></div>`;

    _renderCamRows();
}

function camSearch() { window.camPage = 1; _renderCamRows(); }
function camReset() {
    window.camFilter = { zone: '', status: '', violMin: '' };
    window.camPage = 1;
    _renderCameraTab(document.getElementById('cctvTabContent'));
}

function _filterCam() {
    const f = window.camFilter;
    return (window.mockData.cctvCameras || []).filter(c => {
        if (f.zone   && c.zone   !== f.zone)   return false;
        if (f.status && c.status !== f.status) return false;
        if (f.violMin) {
            const min = parseInt(f.violMin);
            if (min === 1  && c.todayViolCount  < 1)  return false;
            if (min === 5  && c.totalViolCount  < 5)  return false;
            if (min === 10 && c.totalViolCount  < 10) return false;
        }
        return true;
    });
}

function _renderCamRows() {
    const wrap = document.getElementById('camTableWrap');
    if (!wrap) return;

    const list     = _filterCam();
    const total    = list.length;
    const ps       = window.camPageSize;
    const page     = window.camPage;
    const pages    = Math.max(1, Math.ceil(total / ps));
    const pageData = list.slice((page-1)*ps, page*ps);

    if (total === 0) {
        wrap.innerHTML = `<div style="text-align:center;padding:60px;color:#909399;">
            <div style="font-size:36px;margin-bottom:10px;">📷</div>
            <div style="font-size:14px;">暂无监控设备数据，请联系管理员添加设备</div>
        </div>`;
        return;
    }

    let rows = pageData.map(c => {
        const st        = CAM_STATUS_MAP[c.status] || { label: c.status, cls: 'tag-info' };
        const todayRed  = c.todayViolCount > 0 ? 'color:#f56c6c;cursor:pointer;font-weight:600;' : 'color:#303133;';
        const totalRed  = c.totalViolCount > 0 ? 'color:#e6a23c;'                                : 'color:#303133;';
        const unhandRed = c.unhandledCount > 0 ? 'color:#f56c6c;font-weight:600;'               : 'color:#67c23a;';
        const isOnline  = c.status === 'online';

        /* ---- 实时画面播放器 ---- */
        const playerCell = `
            <div style="width:180px;">
                <div id="cam-player-${c.id}"
                     style="position:relative;width:100%;padding-top:56.25%;
                            background:${isOnline?'#1a1a2e':'#2a2a2a'};
                            border-radius:6px;overflow:hidden;
                            opacity:${isOnline?'1':'0.55'};
                            border:1px solid ${isOnline?'#304156':'#444'};">
                    <!-- 画面占位层 -->
                    <div id="cam-screen-${c.id}"
                         style="position:absolute;inset:0;display:flex;flex-direction:column;
                                align-items:center;justify-content:center;transition:all .3s;">
                        <div style="font-size:22px;margin-bottom:4px;">🎥</div>
                        <div id="cam-label-${c.id}"
                             style="font-size:10px;color:#aaa;text-align:center;padding:0 6px;line-height:1.4;">
                            ${isOnline ? c.cameraName + '<br>实时画面' : '设备离线<br>无法播放实时画面'}
                        </div>
                    </div>
                    <!-- 在线状态角标 -->
                    <div style="position:absolute;top:4px;right:5px;
                                background:${isOnline?'rgba(103,194,58,.85)':'rgba(245,108,108,.85)'};
                                color:#fff;font-size:9px;padding:1px 5px;border-radius:3px;font-weight:600;">
                        ${isOnline?'● 在线':'● 离线'}
                    </div>
                    <!-- 极简控制栏 -->
                    <div style="position:absolute;bottom:0;left:0;right:0;
                                background:rgba(0,0,0,.55);padding:3px 6px;
                                display:flex;align-items:center;justify-content:space-between;">
                        <button id="cam-playbtn-${c.id}"
                                onclick="camTogglePlay(${c.id},${isOnline?'true':'false'})"
                                title="${isOnline?'播放/暂停':'设备离线'}"
                                style="background:none;border:none;color:${isOnline?'#fff':'#666'};
                                       font-size:13px;cursor:${isOnline?'pointer':'not-allowed'};
                                       padding:0;line-height:1;" ${isOnline?'':'disabled'}>▶</button>
                        <button onclick="${isOnline?'camFullscreen('+c.id+')':'void(0)'}"
                                title="${isOnline?'全屏':'设备离线'}"
                                style="background:none;border:none;color:${isOnline?'#fff':'#666'};
                                       font-size:11px;cursor:${isOnline?'pointer':'not-allowed'};
                                       padding:0;line-height:1;" ${isOnline?'':'disabled'}>⛶</button>
                    </div>
                </div>
            </div>`;

        return `<tr>
            <td style="font-family:monospace;font-size:12px;">${c.cameraNo}</td>
            <td style="font-weight:600;">${c.cameraName}</td>
            <td>${playerCell}</td>
            <td>${c.zoneName}</td>
            <td style="font-size:12px;color:#606266;">${c.model}</td>
            <td><span class="tag ${st.cls}">${st.label}</span></td>
            <td style="font-family:monospace;font-size:12px;">${c.ip}</td>
            <td style="font-size:12px;">${c.location}</td>
            <td style="font-size:12px;color:#909399;">${c.lastOnlineTime}</td>
            <td><span style="${todayRed}" onclick="cctvJumpViol(${c.id})">${c.todayViolCount}</span></td>
            <td><span style="${totalRed}">${c.totalViolCount}</span></td>
            <td><span style="${unhandRed}">${c.unhandledCount}</span></td>
            <td>
                <button class="btn btn-info btn-sm" onclick="showCamDetail(${c.id})">详情</button>
                <button class="btn btn-default btn-sm" onclick="openCamReplay(${c.id})">回放</button>
            </td>
        </tr>`;
    }).join('');

    wrap.innerHTML = `
        <div style="overflow-x:auto;">
        <table class="data-table" style="table-layout:fixed;width:100%;">
            <colgroup>
                <col style="width:130px;"><col style="width:90px;"><col style="width:190px;">
                <col style="width:70px;"><col style="width:130px;"><col style="width:70px;">
                <col style="width:120px;"><col style="width:130px;"><col style="width:120px;">
                <col style="width:60px;"><col style="width:60px;"><col style="width:55px;">
                <col style="width:110px;">
            </colgroup>
            <thead><tr>
                <th>监控编号</th><th>监控名称</th><th>实时画面</th><th>工位</th><th>设备型号</th>
                <th>在线状态</th><th>IP地址</th><th>安装位置</th><th>最后在线</th>
                <th title="今日违规数">今日违规</th><th title="累计违规数">累计违规</th>
                <th title="未处理预警数">未处理</th><th>操作</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
        </div>
        <div style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="camPaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="camPaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function camPaginate(p) {
    const pages = Math.max(1, Math.ceil(_filterCam().length / window.camPageSize));
    window.camPage = Math.max(1, Math.min(p, pages));
    _renderCamRows();
}

/* ============================================================
   实时播放器交互
   ============================================================ */
/* 播放状态记录：cameraId → boolean (true=playing) */
window._camPlayState = {};

function camTogglePlay(cameraId, isOnline) {
    if (!isOnline) return;
    const playing = !window._camPlayState[cameraId];
    window._camPlayState[cameraId] = playing;

    const labelEl = document.getElementById('cam-label-' + cameraId);
    const btnEl   = document.getElementById('cam-playbtn-' + cameraId);
    const cam     = (window.mockData.cctvCameras || []).find(c => c.id === cameraId);
    const name    = cam ? cam.cameraName : '';

    if (labelEl) {
        labelEl.innerHTML = playing
            ? `<span style="color:#67c23a;font-weight:600;">● 正在播放<br>实时画面</span>`
            : `${name}<br><span style="color:#e6a23c;">⏸ 已暂停实时画面</span>`;
    }
    if (btnEl) {
        btnEl.textContent = playing ? '⏸' : '▶';
        btnEl.title       = playing ? '暂停' : '播放';
    }
}

function camFullscreen(cameraId) {
    const cam = (window.mockData.cctvCameras || []).find(c => c.id === cameraId);
    if (!cam) return;
    const isPlaying = !!window._camPlayState[cameraId];

    showModal(`
        <div style="background:#1a1a2e;border-radius:8px;position:relative;padding-top:56.25%;overflow:hidden;margin-bottom:12px;">
            <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;">
                <div style="font-size:48px;margin-bottom:10px;">🎥</div>
                <div id="fs-label-${cameraId}" style="font-size:15px;color:#ccc;text-align:center;">
                    ${isPlaying
                        ? `<span style="color:#67c23a;font-weight:600;">● 正在播放实时画面</span>`
                        : cam.cameraName + '<br><span style="font-size:13px;color:#888;">点击播放按钮开始</span>'}
                </div>
            </div>
            <!-- 在线角标 -->
            <div style="position:absolute;top:8px;right:10px;background:rgba(103,194,58,.85);
                        color:#fff;font-size:11px;padding:2px 8px;border-radius:4px;font-weight:600;">
                ● 在线
            </div>
            <!-- 控制栏 -->
            <div style="position:absolute;bottom:0;left:0;right:0;background:rgba(0,0,0,.6);
                        padding:8px 14px;display:flex;align-items:center;gap:14px;">
                <button id="fs-playbtn-${cameraId}"
                        onclick="camFsTogglePlay(${cameraId})"
                        style="background:none;border:none;color:#fff;font-size:20px;cursor:pointer;padding:0;">
                    ${isPlaying ? '⏸' : '▶'}
                </button>
                <span style="color:#888;font-size:12px;flex:1;">${cam.cameraName} · 全屏实时画面</span>
                <button onclick="closeModal()" title="退出全屏"
                        style="background:none;border:none;color:#fff;font-size:14px;cursor:pointer;padding:0;">✕ 退出全屏</button>
            </div>
        </div>
    `, '860px');
}

function camFsTogglePlay(cameraId) {
    const playing = !window._camPlayState[cameraId];
    window._camPlayState[cameraId] = playing;

    const fsLabel = document.getElementById('fs-label-' + cameraId);
    const fsBtn   = document.getElementById('fs-playbtn-' + cameraId);
    const cam     = (window.mockData.cctvCameras || []).find(c => c.id === cameraId);
    const name    = cam ? cam.cameraName : '';

    if (fsLabel) {
        fsLabel.innerHTML = playing
            ? `<span style="color:#67c23a;font-weight:600;">● 正在播放实时画面</span>`
            : `${name}<br><span style="font-size:13px;color:#888;">⏸ 已暂停</span>`;
    }
    if (fsBtn) fsBtn.textContent = playing ? '⏸' : '▶';

    /* 同步缩略图播放状态 */
    const labelEl = document.getElementById('cam-label-' + cameraId);
    const btnEl   = document.getElementById('cam-playbtn-' + cameraId);
    if (labelEl) {
        labelEl.innerHTML = playing
            ? `<span style="color:#67c23a;font-weight:600;">● 正在播放<br>实时画面</span>`
            : `${name}<br><span style="color:#e6a23c;">⏸ 已暂停实时画面</span>`;
    }
    if (btnEl) { btnEl.textContent = playing ? '⏸' : '▶'; btnEl.title = playing ? '暂停' : '播放'; }
}

/* 点击违规数 → 跳转违规Tab并按该摄像头筛选 */
function cctvJumpViol(cameraId) {
    window.violFilter = { cameraId: String(cameraId), zone: '', eventType: '', status: '', startDate: '', endDate: '', confidenceMin: '' };
    window.cctvActiveTab = 'violation';
    window.violPage = 1;
    renderCctvPage(document.getElementById('mainContent'));
}

/* ============================================================
   监控详情弹窗
   ============================================================ */
window._camDetailId  = null;
window._camDetailTab = 'device';

function showCamDetail(id) {
    window._camDetailId  = id;
    window._camDetailTab = 'device';
    _renderCamDetailModal();
}

function _renderCamDetailModal() {
    const c = (window.mockData.cctvCameras || []).find(x => x.id === window._camDetailId);
    if (!c) return;
    const st = CAM_STATUS_MAP[c.status] || { label: c.status, cls: 'tag-info' };
    const tab = window._camDetailTab;

    const tabStyle = (t) =>
        `padding:7px 18px;cursor:pointer;font-size:13px;border-bottom:2px solid ${tab===t?'#409eff':'transparent'};color:${tab===t?'#409eff':'#606266'};background:none;border-top:none;border-left:none;border-right:none;`;

    let body = '';
    if (tab === 'device') {
        body = `<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;margin-top:12px;">
            ${_cctvIR('监控编号',   c.cameraNo)}
            ${_cctvIR('监控名称',   c.cameraName)}
            ${_cctvIR('所属工位',   c.zoneName)}
            ${_cctvIR('设备型号',   c.model)}
            ${_cctvIR('IP地址',     c.ip)}
            ${_cctvIR('安装位置',   c.location)}
            ${_cctvIR('设备厂商',   c.vendor)}
            ${_cctvIR('安装时间',   c.installDate)}
            ${_cctvIR('最后在线',   c.lastOnlineTime)}
            ${_cctvIR('在线状态',   `<span class="tag ${st.cls}">${st.label}</span>`)}
            ${_cctvIR('所属食堂',   c.orgName)}
        </div>`;
    } else if (tab === 'live') {
        body = `<div style="margin-top:12px;">
            <div style="position:relative;width:100%;padding-top:56.25%;background:#1a1a2e;border-radius:8px;overflow:hidden;margin-bottom:14px;">
                <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#aaa;">
                    <div style="font-size:40px;margin-bottom:8px;">🎥</div>
                    <div style="font-size:13px;">当前监控实时画面</div>
                    <div style="font-size:12px;color:#666;margin-top:4px;">${c.cameraName} · ${c.status==='online'?'<span style=color:#67c23a>● 直播中</span>':'<span style=color:#f56c6c>● 离线</span>'}</div>
                </div>
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:10px;">
                ${_cctvIR('画面帧率', c.fps + ' fps')}
                ${_cctvIR('码率',     c.status==='online'?c.bitrate:'—')}
                ${_cctvIR('清晰度',  c.resolution)}
                ${_cctvIR('今日违规', `<span style="color:${c.todayViolCount>0?'#f56c6c':'#303133'};font-weight:600;">${c.todayViolCount} 次</span>`)}
            </div>
        </div>`;
    } else {
        // 预警配置
        const rows = (c.alertTypes || []).map(t =>
            `<div style="display:flex;align-items:center;justify-content:space-between;padding:8px 12px;border-bottom:1px solid #f5f5f5;">
                <span style="font-size:13px;color:#303133;">🔔 ${ALERT_TYPE_LABELS[t]||t}</span>
                <span class="tag tag-success">已开启</span>
            </div>`
        ).join('');
        const allTypes = Object.entries(ALERT_TYPE_LABELS);
        const disabledRows = allTypes
            .filter(([k]) => !(c.alertTypes||[]).includes(k))
            .map(([k,v]) =>
                `<div style="display:flex;align-items:center;justify-content:space-between;padding:8px 12px;border-bottom:1px solid #f5f5f5;">
                    <span style="font-size:13px;color:#c0c4cc;">🔕 ${v}</span>
                    <span class="tag tag-info">未开启</span>
                </div>`
            ).join('');
        body = `<div style="margin-top:12px;border:1px solid #ebeef5;border-radius:4px;overflow:hidden;">
            ${rows}${disabledRows}
        </div>`;
    }

    const html = `
        <div class="modal-title">【${c.cameraNo}】${c.cameraName} 详情</div>
        <div style="display:flex;border-bottom:1px solid #ebeef5;margin-bottom:4px;">
            <button style="${tabStyle('device')}" onclick="cctvDetailTab('device')">设备信息</button>
            <button style="${tabStyle('live')}"   onclick="cctvDetailTab('live')">实时状态</button>
            <button style="${tabStyle('alert')}"  onclick="cctvDetailTab('alert')">预警配置</button>
        </div>
        ${body}
        <div style="display:flex;justify-content:flex-end;margin-top:16px;">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;

    showModal(html, '580px');
}

function cctvDetailTab(tab) {
    window._camDetailTab = tab;
    _renderCamDetailModal();
}

/* ============================================================
   视频回放弹窗
   ============================================================ */
window._replayData = {};

function openCamReplay(id) {
    const c = (window.mockData.cctvCameras || []).find(x => x.id === id);
    if (!c) return;
    const today = new Date().toISOString().slice(0, 10);
    const nowHM = _cctvNowStr().slice(11, 16);
    window._replayData = { cameraId: id, camera: c, date: today, timeStart: '00:00', timeEnd: nowHM, violOnly: false, selectedClip: null };
    _renderReplayModal();
}

function _renderReplayModal() {
    const d  = window._replayData;
    const c  = d.camera;

    // 生成片段列表（Mock：按30分钟分段）
    const clips = _genMockClips(d.date, d.timeStart, d.timeEnd, d.violOnly, c);

    const clipRows = clips.length === 0
        ? `<div style="text-align:center;color:#909399;padding:20px;font-size:13px;">该时间段暂无回放数据</div>`
        : clips.map((cl, i) => `
            <div onclick="cctvSelectClip(${i})"
                 style="padding:8px 10px;cursor:pointer;border-bottom:1px solid #f5f5f5;font-size:12px;
                        background:${d.selectedClip===i?'#ecf5ff':'#fff'};
                        border-left:3px solid ${d.selectedClip===i?'#409eff':'transparent'};transition:all .15s;">
                <div style="font-weight:600;color:#303133;">${cl.label}</div>
                ${cl.hasViol?`<span style="color:#f56c6c;font-size:11px;">⚠ 含违规片段</span>`:''}
            </div>`).join('');

    const playLabel = d.selectedClip !== null && clips[d.selectedClip]
        ? `${c.cameraName} · ${clips[d.selectedClip].label}`
        : `${c.cameraName} · 请选择片段`;

    showModal(`
        <div class="modal-title">📹 视频回放 — ${c.cameraName}</div>
        <div style="display:grid;grid-template-columns:260px 1fr;gap:16px;min-height:420px;">

            <!-- 检索区 -->
            <div style="border-right:1px solid #ebeef5;padding-right:16px;">
                <div style="font-size:12px;font-weight:600;color:#909399;margin-bottom:10px;">🔍 回放检索</div>
                <div style="margin-bottom:8px;">
                    <label style="font-size:12px;color:#606266;display:block;margin-bottom:4px;">日期</label>
                    <input type="date" class="filter-input" value="${d.date}" style="width:100%;font-size:12px;"
                           onchange="window._replayData.date=this.value;window._replayData.selectedClip=null;_renderReplayModal()">
                </div>
                <div style="margin-bottom:8px;display:flex;gap:6px;">
                    <div style="flex:1;">
                        <label style="font-size:12px;color:#606266;display:block;margin-bottom:4px;">开始时间</label>
                        <input type="time" class="filter-input" value="${d.timeStart}" style="width:100%;font-size:12px;"
                               onchange="window._replayData.timeStart=this.value;window._replayData.selectedClip=null;_renderReplayModal()">
                    </div>
                    <div style="flex:1;">
                        <label style="font-size:12px;color:#606266;display:block;margin-bottom:4px;">结束时间</label>
                        <input type="time" class="filter-input" value="${d.timeEnd}" style="width:100%;font-size:12px;"
                               onchange="window._replayData.timeEnd=this.value;window._replayData.selectedClip=null;_renderReplayModal()">
                    </div>
                </div>
                <div style="margin-bottom:8px;">
                    <label style="font-size:12px;color:#606266;display:block;margin-bottom:4px;">工位</label>
                    <input class="filter-input" value="${c.zoneName}" readonly style="width:100%;font-size:12px;background:#f5f7fa;cursor:not-allowed;">
                </div>
                <div style="margin-bottom:12px;display:flex;align-items:center;gap:6px;">
                    <input type="checkbox" id="violOnlyChk" ${d.violOnly?'checked':''}
                           onchange="window._replayData.violOnly=this.checked;window._replayData.selectedClip=null;_renderReplayModal()">
                    <label for="violOnlyChk" style="font-size:12px;color:#606266;cursor:pointer;">仅回放违规片段</label>
                </div>
                <div style="font-size:12px;font-weight:600;color:#909399;margin-bottom:6px;">
                    📋 片段列表 <span style="font-weight:400;color:#c0c4cc;">(${clips.length}个)</span>
                </div>
                <div style="border:1px solid #ebeef5;border-radius:4px;max-height:240px;overflow-y:auto;">
                    ${clipRows}
                </div>
            </div>

            <!-- 播放区 -->
            <div style="display:flex;flex-direction:column;">
                <div style="position:relative;width:100%;padding-top:56.25%;background:#1a1a2e;border-radius:8px;overflow:hidden;flex-shrink:0;">
                    <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#aaa;">
                        <div style="font-size:36px;margin-bottom:8px;">▶️</div>
                        <div style="font-size:13px;text-align:center;padding:0 16px;">视频回放</div>
                        <div style="font-size:12px;color:#666;margin-top:4px;text-align:center;padding:0 12px;">${playLabel}</div>
                    </div>
                </div>
                <!-- 播放控制栏 -->
                <div style="background:#2d2d2d;border-radius:0 0 8px 8px;padding:8px 12px;display:flex;align-items:center;gap:10px;">
                    <button style="background:none;border:none;color:#fff;font-size:16px;cursor:pointer;" title="播放/暂停">⏯</button>
                    <div style="flex:1;height:4px;background:#555;border-radius:2px;cursor:pointer;">
                        <div style="width:${d.selectedClip!==null?'30':'0'}%;height:100%;background:#409eff;border-radius:2px;"></div>
                    </div>
                    <select style="background:#444;color:#fff;border:none;font-size:11px;border-radius:3px;padding:2px 4px;">
                        <option>0.5x</option><option selected>1x</option><option>1.5x</option><option>2x</option>
                    </select>
                    <button style="background:none;border:none;color:#fff;font-size:14px;cursor:pointer;" title="音量">🔊</button>
                    <button style="background:none;border:none;color:#fff;font-size:14px;cursor:pointer;" title="全屏">⛶</button>
                </div>
            </div>
        </div>
        <div style="display:flex;justify-content:flex-end;margin-top:14px;">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '860px');
}

function cctvSelectClip(idx) {
    window._replayData.selectedClip = idx;
    _renderReplayModal();
}

function _genMockClips(date, timeStart, timeEnd, violOnly, camera) {
    // 将时间段按30分钟切片，生成Mock片段
    const [sh, sm] = timeStart.split(':').map(Number);
    const [eh, em] = timeEnd.split(':').map(Number);
    const startMin = sh * 60 + sm;
    const endMin   = eh * 60 + em;
    if (endMin <= startMin) return [];

    const violTimes = (window.mockData.cctvViolations || [])
        .filter(v => v.cameraId === camera.id && v.eventTime.startsWith(date))
        .map(v => {
            const t = v.eventTime.slice(11, 16);
            const [hh, mm] = t.split(':').map(Number);
            return hh * 60 + mm;
        });

    const clips = [];
    for (let s = startMin; s < endMin; s += 30) {
        const e = Math.min(s + 30, endMin);
        const pad = n => String(n).padStart(2, '0');
        const label = `${pad(Math.floor(s/60))}:${pad(s%60)}-${pad(Math.floor(e/60))}:${pad(e%60)} ${camera.zoneName}操作片段`;
        const hasViol = violTimes.some(t => t >= s && t < e);
        if (violOnly && !hasViol) continue;
        clips.push({ label, hasViol });
    }
    return clips;
}

/* ============================================================
   违规记录 Tab
   ============================================================ */
function _renderViolationTab(container) {
    const f = window.violFilter;

    // 统计数据
    const today = new Date().toISOString().slice(0, 10);
    const todayCount = (window.mockData.cctvViolations || []).filter(v => v.eventTime.startsWith(today)).length;
    const unhandCount = (window.mockData.cctvViolations || []).filter(v => v.status === 'unhandled').length;

    // 下拉选项
    const camOpts = (window.mockData.cctvCameras || [])
        .map(c => `<option value="${c.id}" ${f.cameraId==c.id?'selected':''}>${c.cameraName}</option>`).join('');
    const zoneOpts = Object.entries(CAM_ZONE_MAP)
        .map(([v,l]) => `<option value="${v}" ${f.zone===v?'selected':''}>${l}</option>`).join('');
    const evtOpts = Object.entries(VIOL_EVENT_MAP)
        .map(([v,l]) => `<option value="${v}" ${f.eventType===v?'selected':''}>${l}</option>`).join('');

    container.innerHTML = `
        <!-- 统计卡 -->
        <div style="display:flex;gap:12px;margin-bottom:16px;">
            <div style="flex:1;background:linear-gradient(135deg,#f56c6c,#e54040);border-radius:8px;padding:14px 18px;color:#fff;">
                <div style="font-size:11px;opacity:.85;margin-bottom:4px;">今日累计违规数</div>
                <div style="font-size:28px;font-weight:700;">${todayCount}</div>
            </div>
            <div style="flex:1;background:linear-gradient(135deg,#e6a23c,#d08700);border-radius:8px;padding:14px 18px;color:#fff;">
                <div style="font-size:11px;opacity:.85;margin-bottom:4px;">未处理预警数</div>
                <div style="font-size:28px;font-weight:700;">${unhandCount}</div>
            </div>
            <div style="flex:1;background:linear-gradient(135deg,#409eff,#2575e8);border-radius:8px;padding:14px 18px;color:#fff;">
                <div style="font-size:11px;opacity:.85;margin-bottom:4px;">违规记录总数</div>
                <div style="font-size:28px;font-weight:700;">${(window.mockData.cctvViolations||[]).length}</div>
            </div>
        </div>
        <!-- 筛选栏 -->
        <div class="filter-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
            <select class="filter-input" style="width:130px;" onchange="window.violFilter.cameraId=this.value">
                <option value="" ${!f.cameraId?'selected':''}>全部监控</option>
                ${camOpts}
            </select>
            <select class="filter-input" style="width:120px;" onchange="window.violFilter.zone=this.value">
                <option value="" ${!f.zone?'selected':''}>全部工位</option>
                ${zoneOpts}
            </select>
            <select class="filter-input" style="width:140px;" onchange="window.violFilter.eventType=this.value">
                <option value="" ${!f.eventType?'selected':''}>全部事件类型</option>
                ${evtOpts}
            </select>
            <select class="filter-input" style="width:120px;" onchange="window.violFilter.status=this.value">
                <option value="" ${!f.status?'selected':''}>全部状态</option>
                <option value="unhandled" ${f.status==='unhandled'?'selected':''}>未处理</option>
                <option value="handled"   ${f.status==='handled'  ?'selected':''}>已处理</option>
            </select>
            <select class="filter-input" style="width:130px;" onchange="window.violFilter.confidenceMin=this.value">
                <option value="" ${!f.confidenceMin?'selected':''}>置信度不限</option>
                <option value="80" ${f.confidenceMin==='80'?'selected':''}>≥80%</option>
                <option value="90" ${f.confidenceMin==='90'?'selected':''}>≥90%</option>
            </select>
            <input type="date" class="filter-input" value="${f.startDate}" title="开始日期"
                   oninput="window.violFilter.startDate=this.value" style="width:145px;">
            <span style="color:#606266;font-size:13px;">至</span>
            <input type="date" class="filter-input" value="${f.endDate}" title="结束日期"
                   oninput="window.violFilter.endDate=this.value" style="width:145px;">
            <button class="btn btn-primary" onclick="violSearch()">🔍 查询</button>
            <button class="btn btn-default" onclick="violReset()">↺ 重置</button>
        </div>
        <div id="violTableWrap"></div>`;

    _renderViolRows();
}

function violSearch() { window.violPage = 1; _renderViolRows(); }
function violReset() {
    window.violFilter = { cameraId: '', zone: '', eventType: '', status: '', startDate: '', endDate: '', confidenceMin: '' };
    window.violPage = 1;
    _renderViolationTab(document.getElementById('cctvTabContent'));
}

function _filterViol() {
    const f = window.violFilter;
    return (window.mockData.cctvViolations || []).filter(v => {
        if (f.cameraId  && String(v.cameraId)   !== f.cameraId)  return false;
        if (f.zone      && v.zone               !== f.zone)       return false;
        if (f.eventType && v.eventType          !== f.eventType)  return false;
        if (f.status    && v.status             !== f.status)     return false;
        if (f.startDate && v.eventTime.slice(0,10) < f.startDate) return false;
        if (f.endDate   && v.eventTime.slice(0,10) > f.endDate)   return false;
        if (f.confidenceMin && v.confidence < parseInt(f.confidenceMin)) return false;
        return true;
    });
}

function _renderViolRows() {
    const wrap = document.getElementById('violTableWrap');
    if (!wrap) return;

    const list     = _filterViol();
    const total    = list.length;
    const ps       = window.violPageSize;
    const page     = window.violPage;
    const pages    = Math.max(1, Math.ceil(total / ps));
    const pageData = list.slice((page-1)*ps, page*ps);

    let rows = '';
    if (pageData.length === 0) {
        rows = `<tr><td colspan="9" style="text-align:center;color:#999;padding:32px;">暂无违规记录</td></tr>`;
    } else {
        pageData.forEach(v => {
            const st  = VIOL_STATUS_MAP[v.status] || { label: v.status, cls: 'tag-info' };
            const evt = VIOL_EVENT_MAP[v.eventType] || v.eventType;
            const conf = v.confidence >= 90 ? `<span style="color:#f56c6c;font-weight:600;">${v.confidence}%</span>`
                       : `<span style="color:#e6a23c;">${v.confidence}%</span>`;
            rows += `<tr>
                <td style="font-family:monospace;font-size:12px;">${v.violNo}</td>
                <td><span style="font-size:12px;">${v.cameraName}</span></td>
                <td>${v.zoneName}</td>
                <td><span style="background:#f0f9eb;color:#67c23a;padding:2px 8px;border-radius:3px;font-size:12px;">${evt}</span></td>
                <td>${conf}</td>
                <td style="font-family:monospace;font-size:12px;">${v.eventTime}</td>
                <td><span class="tag ${st.cls}">${st.label}</span></td>
                <td>
                    <button class="btn btn-info btn-sm" onclick="openViolClip(${v.id})" style="font-size:11px;padding:2px 8px;">查看片段</button>
                </td>
                <td>
                    ${v.status==='unhandled'
                        ? `<button class="btn btn-warning btn-sm" onclick="markViolHandled(${v.id})">标记处理</button>`
                        : `<span style="font-size:12px;color:#67c23a;">✓ ${v.handlerName||''}</span>`
                    }
                </td>
            </tr>`;
        });
    }

    wrap.innerHTML = `
        <table class="data-table">
            <thead><tr>
                <th>违规编号</th><th>监控名称</th><th>工位</th><th>事件类型</th>
                <th>置信度</th><th>时间戳</th><th>预警状态</th><th>违规片段</th><th style="width:100px;">操作</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
        <div style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="violPaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="violPaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function violPaginate(p) {
    const pages = Math.max(1, Math.ceil(_filterViol().length / window.violPageSize));
    window.violPage = Math.max(1, Math.min(p, pages));
    _renderViolRows();
}

/* 查看违规片段小窗 */
function openViolClip(id) {
    const v = (window.mockData.cctvViolations || []).find(x => x.id === id);
    if (!v) return;
    const evt = VIOL_EVENT_MAP[v.eventType] || v.eventType;
    showModal(`
        <div class="modal-title">违规片段 — ${v.violNo}</div>
        <div style="background:#1a1a2e;border-radius:8px;position:relative;padding-top:56.25%;overflow:hidden;margin-bottom:14px;">
            <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;">
                <div style="font-size:36px;margin-bottom:8px;">⚠️</div>
                <div style="font-size:14px;color:#f56c6c;font-weight:600;">${evt}</div>
                <div style="font-size:12px;color:#888;margin-top:6px;">${v.cameraName} · ${v.eventTime}</div>
                <div style="font-size:11px;color:#666;margin-top:3px;">置信度 ${v.confidence}%</div>
            </div>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:14px;">
            ${_cctvIR('违规编号', v.violNo)}
            ${_cctvIR('监控名称', v.cameraName)}
            ${_cctvIR('所属工位', v.zoneName)}
            ${_cctvIR('事件类型', evt)}
            ${_cctvIR('置信度',   v.confidence + '%')}
            ${_cctvIR('时间戳',   v.eventTime)}
            ${_cctvIR('预警状态', `<span class="tag ${VIOL_STATUS_MAP[v.status].cls}">${VIOL_STATUS_MAP[v.status].label}</span>`)}
        </div>
        <div style="display:flex;justify-content:flex-end;gap:8px;">
            ${v.status==='unhandled'?`<button class="btn btn-warning" onclick="markViolHandled(${v.id});closeModal()">标记处理</button>`:''}
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '520px');
}

/* 标记处理 */
function markViolHandled(id) {
    const v = (window.mockData.cctvViolations || []).find(x => x.id === id);
    if (!v) return;
    v.status      = 'handled';
    v.handlerId   = 1;
    v.handlerName = '赵厨长';
    v.handleTime  = _cctvNowStr();
    v.handleRemark = '已确认并处理';

    // 同步更新摄像头统计
    const cam = (window.mockData.cctvCameras || []).find(c => c.id === v.cameraId);
    if (cam && cam.unhandledCount > 0) {
        cam.unhandledCount--;
    }
    showToast('预警已标记为处理');
    _renderViolRows();
    // 同步刷新摄像头列表若当前在camera tab
    if (window.cctvActiveTab === 'camera') _renderCamRows();
}

/* ============================================================
   工具函数
   ============================================================ */
function _cctvNowStr() {
    const d = new Date();
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function _cctvIR(label, value) {
    return `<div style="display:flex;gap:4px;font-size:13px;align-items:flex-start;">
        <span style="color:#909399;white-space:nowrap;min-width:72px;">${label}：</span>
        <span style="color:#303133;">${value}</span>
    </div>`;
}
