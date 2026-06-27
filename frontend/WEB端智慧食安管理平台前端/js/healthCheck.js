/**
 * healthCheck.js — 智能人脸晨检管理模块
 *
 * 运行验证说明：
 * ① 点击左侧「智能人脸晨检」导航 → 进入晨检管理界面
 * ② 点击「生成今日晨检任务」→ 弹窗选择组织+日期 → 预览关联员工 → 点击「确认生成」
 *   → 提示"晨检任务生成成功，共为X名员工生成任务" → 列表自动刷新
 * ③ 已生成任务后再次生成同日期 → 弹出"是否重新生成"确认框
 * ④ 列表支持按员工姓名/组织/日期范围/晨检状态/人脸核验状态筛选、分页
 * ⑤ 点击任意行「详情」按钮 → 弹窗分3个区域展示所有只读字段
 *   已完成记录：体温36.5℃、健康项均否、晨检结果合格
 *
 * 核心改动点：
 * - 新增晨检管理导航入口（sidebar.js + index.html）
 * - 新增 mockData.healthCheckTasks 数据（mock-data.js）
 * - 新增晨检任务生成Mock逻辑（关联组织+员工数据）
 * - 新增晨检列表筛选/分页/状态标签展示逻辑
 * - 新增晨检详情弹窗（3区域只读字段）
 */

/* ============================================================
   常量
   ============================================================ */
const HC_STATUS_MAP = {
    pending:   { label: '未晨检',   cls: 'tag-primary' },
    done:      { label: '已完成',   cls: 'tag-success' },
    overdue:   { label: '逾期未检', cls: 'tag-danger'  }
};

const HC_FACE_STATUS_MAP = {
    unverified: { label: '未核验', cls: 'tag-info'    },
    verified:   { label: '已核验', cls: 'tag-success' }
};

const HC_RESULT_MAP = {
    pass:        '合格',
    fail:        '不合格',
    unfinished:  '未完成'
};

const HC_POSITION_MAP = {
    chef:       '厨师',
    cookworker: '厨工',
    manager:    '店长',
    purchaser:  '采购员'
};

/* 需要晨检的岗位 */
const HC_NEED_CHECK_POSITIONS = ['chef', 'cookworker', 'manager'];

/* ============================================================
   全局状态
   ============================================================ */
window.hcPage      = 1;
window.hcPageSize  = 10;
window.hcFilter    = { keyword: '', orgId: '', startDate: '', endDate: '', status: '', faceStatus: '' };

/* ============================================================
   主入口
   ============================================================ */
function renderHealthCheckPage(container) {
    window.hcPage = 1;

    container.innerHTML = `
        <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
            <div style="font-size:16px;font-weight:600;color:#303133;">🤖 智能人脸晨检管理</div>
            <button class="btn btn-primary" onclick="openGenHealthCheckModal()">＋ 生成今日晨检任务</button>
        </div>
        <div id="hcFilterBar"></div>
        <div id="hcTableWrap"></div>`;

    _renderHcFilter();
    _renderHcRows();
}

/* ============================================================
   筛选栏
   ============================================================ */
function _renderHcFilter() {
    const el = document.getElementById('hcFilterBar');
    if (!el) return;
    const f = window.hcFilter;

    // 组织下拉
    const orgOpts = (window.mockData.orgs || [])
        .filter(o => o.status === 'active')
        .map(o => `<option value="${o.id}" ${f.orgId==o.id?'selected':''}>${o.orgName}</option>`)
        .join('');

    el.innerHTML = `
        <div class="filter-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
            <input class="filter-input" placeholder="员工姓名/编号" value="${f.keyword}"
                   oninput="window.hcFilter.keyword=this.value" style="width:180px;">
            <select class="filter-input" style="width:150px;" onchange="window.hcFilter.orgId=this.value">
                <option value="" ${!f.orgId?'selected':''}>全部组织</option>
                ${orgOpts}
            </select>
            <input type="date" class="filter-input" value="${f.startDate}"
                   oninput="window.hcFilter.startDate=this.value" title="晨检开始日期" style="width:148px;">
            <span style="color:#606266;font-size:13px;">至</span>
            <input type="date" class="filter-input" value="${f.endDate}"
                   oninput="window.hcFilter.endDate=this.value" title="晨检结束日期" style="width:148px;">
            <select class="filter-input" style="width:120px;" onchange="window.hcFilter.status=this.value">
                <option value="" ${f.status===''?'selected':''}>全部状态</option>
                <option value="pending" ${f.status==='pending'?'selected':''}>未晨检</option>
                <option value="done"    ${f.status==='done'   ?'selected':''}>已完成</option>
                <option value="overdue" ${f.status==='overdue'?'selected':''}>逾期未检</option>
            </select>
            <select class="filter-input" style="width:130px;" onchange="window.hcFilter.faceStatus=this.value">
                <option value="" ${f.faceStatus===''?'selected':''}>人脸核验全部</option>
                <option value="unverified" ${f.faceStatus==='unverified'?'selected':''}>未核验</option>
                <option value="verified"   ${f.faceStatus==='verified'  ?'selected':''}>已核验</option>
            </select>
            <button class="btn btn-primary" onclick="hcSearch()">🔍 查询</button>
            <button class="btn btn-default" onclick="hcResetFilter()">↺ 重置</button>
        </div>`;
}

function hcSearch() {
    window.hcPage = 1;
    _renderHcRows();
}

function hcResetFilter() {
    window.hcFilter = { keyword: '', orgId: '', startDate: '', endDate: '', status: '', faceStatus: '' };
    window.hcPage = 1;
    renderHealthCheckPage(document.getElementById('mainContent'));
}

/* ============================================================
   列表渲染
   ============================================================ */
function _filterHc() {
    const f = window.hcFilter;
    return (window.mockData.healthCheckTasks || []).filter(t => {
        if (f.keyword) {
            const kw = f.keyword.toLowerCase();
            if (!t.employeeName.toLowerCase().includes(kw) &&
                !t.checkNo.toLowerCase().includes(kw) &&
                !t.employeeNo.toLowerCase().includes(kw)) return false;
        }
        if (f.orgId && String(t.orgId) !== String(f.orgId)) return false;
        if (f.startDate && t.checkDate < f.startDate) return false;
        if (f.endDate   && t.checkDate > f.endDate)   return false;
        if (f.status    && t.status !== f.status)     return false;
        if (f.faceStatus && t.faceVerifyStatus !== f.faceStatus) return false;
        return true;
    });
}

function _renderHcRows() {
    const wrap = document.getElementById('hcTableWrap');
    if (!wrap) return;

    const list     = _filterHc();
    const total    = list.length;
    const ps       = window.hcPageSize;
    const page     = window.hcPage;
    const pages    = Math.max(1, Math.ceil(total / ps));
    const start    = (page - 1) * ps;
    const pageData = list.slice(start, start + ps);

    if (total === 0 && !(window.mockData.healthCheckTasks || []).length) {
        wrap.innerHTML = `
            <div style="text-align:center;padding:60px 0;color:#909399;">
                <div style="font-size:40px;margin-bottom:12px;">🤖</div>
                <div style="font-size:15px;margin-bottom:8px;">暂无晨检任务数据</div>
                <div style="font-size:13px;">可点击「生成今日晨检任务」创建</div>
            </div>`;
        return;
    }

    let rows = '';
    if (pageData.length === 0) {
        rows = `<tr><td colspan="9" style="text-align:center;color:#999;padding:32px;">筛选条件下无匹配数据</td></tr>`;
    } else {
        pageData.forEach(t => {
            const st = HC_STATUS_MAP[t.status]           || { label: t.status,           cls: 'tag-info' };
            const fs = HC_FACE_STATUS_MAP[t.faceVerifyStatus] || { label: t.faceVerifyStatus, cls: 'tag-info' };
            const res = HC_RESULT_MAP[t.checkResult] || t.checkResult;
            rows += `<tr>
                <td style="font-family:monospace;font-size:12px;">${t.checkNo}</td>
                <td>${t.employeeName}</td>
                <td>${t.orgName}</td>
                <td>${HC_POSITION_MAP[t.position]||t.position}</td>
                <td>${t.checkDate}</td>
                <td><span class="tag ${st.cls}">${st.label}</span></td>
                <td><span class="tag ${fs.cls}">${fs.label}</span></td>
                <td style="color:${t.checkResult==='pass'?'#67c23a':t.checkResult==='fail'?'#f56c6c':'#909399'}">${res}</td>
                <td>
                    <button class="btn btn-info btn-sm" onclick="showHcDetail(${t.id})">详情</button>
                </td>
            </tr>`;
        });
    }

    wrap.innerHTML = `
        <table class="data-table">
            <thead>
                <tr>
                    <th>晨检任务编号</th>
                    <th>员工姓名</th>
                    <th>所属组织</th>
                    <th>岗位</th>
                    <th>晨检日期</th>
                    <th>晨检状态</th>
                    <th>人脸核验</th>
                    <th>晨检结果</th>
                    <th style="width:80px;">操作</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
        <div class="pagination" style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="hcPaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="hcPaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function hcPaginate(p) {
    const total = _filterHc().length;
    const pages = Math.max(1, Math.ceil(total / window.hcPageSize));
    window.hcPage = Math.max(1, Math.min(p, pages));
    _renderHcRows();
}

/* ============================================================
   生成晨检任务弹窗
   ============================================================ */
function openGenHealthCheckModal() {
    const today = new Date().toISOString().slice(0, 10);

    // 组织下拉选项
    const orgOpts = (window.mockData.orgs || [])
        .filter(o => o.status === 'active')
        .map(o => {
            const indent = '　'.repeat(Math.max(0, o.level - 1));
            return `<option value="${o.id}">${indent}${o.orgName}</option>`;
        }).join('');

    showModal(`
        <div class="modal-title">生成晨检任务</div>
        <div style="margin-bottom:4px;color:#606266;font-size:13px;background:#f4f7fd;border-radius:4px;padding:8px 12px;margin-bottom:12px;">
            💡 系统将基于所选组织下的在职后厨员工自动生成晨检任务
        </div>
        <div style="display:grid;grid-template-columns:90px 1fr;gap:12px;align-items:center;margin-bottom:12px;">
            <label style="font-size:14px;color:#606266;text-align:right;"><span style="color:#f56c6c;">*</span> 所属组织：</label>
            <select id="hcGenOrg" class="filter-input" style="width:100%;" onchange="_hcPreview()">
                <option value="">请选择组织</option>
                ${orgOpts}
            </select>
            <label style="font-size:14px;color:#606266;text-align:right;"><span style="color:#f56c6c;">*</span> 晨检日期：</label>
            <input type="date" id="hcGenDate" class="filter-input" value="${today}" style="width:180px;" onchange="_hcPreview()">
        </div>
        <div id="hcGenPreview" style="min-height:80px;border-top:1px solid #ebeef5;padding-top:12px;"></div>
        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px;">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" id="btnConfirmHc" onclick="confirmGenHealthCheck()" disabled>确认生成</button>
        </div>
    `, '560px');
}

window._hcPreviewEmps = [];

function _hcPreview() {
    const orgSel  = document.getElementById('hcGenOrg');
    const dateEl  = document.getElementById('hcGenDate');
    const preview = document.getElementById('hcGenPreview');
    const btn     = document.getElementById('btnConfirmHc');
    if (!orgSel || !dateEl || !preview) return;

    const orgId = orgSel.value;
    const date  = dateEl.value;

    if (!orgId || !date) {
        preview.innerHTML = '<span style="color:#909399;font-size:13px;">请先选择组织和日期</span>';
        if (btn) btn.disabled = true;
        return;
    }

    // 找该组织及下级组织ID
    const orgIds = _hcGetOrgAndChildren(parseInt(orgId));

    // 过滤需晨检在职员工
    const emps = (window.mockData.employees || []).filter(e =>
        e.status === 'active' &&
        orgIds.includes(e.orgId) &&
        HC_NEED_CHECK_POSITIONS.includes(e.position)
    );

    window._hcPreviewEmps = emps;

    if (emps.length === 0) {
        preview.innerHTML = `<div style="color:#e6a23c;font-size:13px;">该组织下暂无需晨检的在职员工（后厨/厨工/店长岗位）</div>`;
        if (btn) btn.disabled = true;
        return;
    }

    // 检查是否当日已生成
    const existCount = (window.mockData.healthCheckTasks || [])
        .filter(t => orgIds.includes(t.orgId) && t.checkDate === date).length;

    const warnHtml = existCount > 0
        ? `<div style="background:#fef0f0;border:1px solid #fde2e2;border-radius:4px;padding:8px 12px;margin-bottom:10px;font-size:12px;color:#f56c6c;">⚠️ 该日期已生成过 ${existCount} 条晨检任务，确认后将覆盖重新生成</div>`
        : '';

    let empRows = emps.map(e => `
        <tr>
            <td>${e.empNo}</td>
            <td>${e.name}</td>
            <td>${e.orgName}</td>
            <td>${HC_POSITION_MAP[e.position]||e.position}</td>
        </tr>`).join('');

    preview.innerHTML = `
        ${warnHtml}
        <div style="margin-bottom:8px;font-size:13px;color:#409eff;">将为以下 <b>${emps.length}</b> 名员工生成晨检任务：</div>
        <div style="max-height:200px;overflow-y:auto;">
            <table class="data-table" style="font-size:12px;">
                <thead><tr><th>员工编号</th><th>姓名</th><th>组织</th><th>岗位</th></tr></thead>
                <tbody>${empRows}</tbody>
            </table>
        </div>`;

    if (btn) btn.disabled = false;
}

function _hcGetOrgAndChildren(rootId) {
    const all  = window.mockData.orgs || [];
    const ids  = [rootId];
    let   prev = [rootId];
    while (true) {
        const children = all.filter(o => prev.includes(o.parentId)).map(o => o.id);
        if (!children.length) break;
        ids.push(...children);
        prev = children;
    }
    return ids;
}

function confirmGenHealthCheck() {
    const orgSel = document.getElementById('hcGenOrg');
    const dateEl = document.getElementById('hcGenDate');
    if (!orgSel || !dateEl) return;

    const orgId = parseInt(orgSel.value);
    const date  = dateEl.value;
    const emps  = window._hcPreviewEmps || [];

    if (!orgId || !date || emps.length === 0) {
        showToast('请完整填写并预览后再生成', 'error');
        return;
    }

    const orgIds = _hcGetOrgAndChildren(orgId);

    // 覆盖：删除已有同组织+日期任务
    window.mockData.healthCheckTasks = (window.mockData.healthCheckTasks || [])
        .filter(t => !(orgIds.includes(t.orgId) && t.checkDate === date));

    const existing = window.mockData.healthCheckTasks;
    const maxId    = existing.reduce((m, t) => Math.max(m, t.id), 0);
    const dateStr  = date.replace(/-/g, '');
    const now      = new Date();
    const nowStr   = _hcFmtDate(now);

    emps.forEach((e, i) => {
        const id  = maxId + i + 1;
        const seq = String(i + 1).padStart(3, '0');
        existing.push({
            id:               id,
            checkNo:          `HC-${dateStr}-${seq}`,
            employeeId:       e.id,
            employeeNo:       e.empNo,
            employeeName:     e.name,
            orgId:            e.orgId,
            orgName:          e.orgName,
            deptId:           e.deptId,
            deptName:         e.deptName,
            position:         e.position,
            checkDate:        date,
            checkTimeStart:   `${date} 06:00:00`,
            checkTimeEnd:     `${date} 09:00:00`,
            /* 人脸核验 */
            faceVerifyStatus: 'unverified',
            faceImageUrl:     '',
            faceMatchScore:   null,
            faceVerifyTime:   null,
            /* 健康状态 */
            temperature:      null,
            hasFever:         false,
            hasCough:         false,
            hasSkinDisease:   false,
            handHygiene:      null,
            uniformCheck:     null,
            checkResult:      'unfinished',
            status:           'pending',
            checkerId:        null,
            checkerName:      null,
            checkFinishTime:  null,
            remark:           '',
            tenantId:         1,
            createdAt:        nowStr,
            updatedAt:        nowStr
        });
    });

    window.mockData.healthCheckTasks = existing;
    closeModal();
    showToast(`晨检任务生成成功，共为 ${emps.length} 名员工生成任务`);
    renderHealthCheckPage(document.getElementById('mainContent'));
}

/* ============================================================
   晨检详情弹窗
   ============================================================ */
function showHcDetail(id) {
    const t = (window.mockData.healthCheckTasks || []).find(x => x.id === id);
    if (!t) { showToast('数据不存在', 'error'); return; }

    const st  = HC_STATUS_MAP[t.status]           || { label: t.status,           cls: 'tag-info' };
    const fs  = HC_FACE_STATUS_MAP[t.faceVerifyStatus] || { label: t.faceVerifyStatus, cls: 'tag-info' };
    const res = HC_RESULT_MAP[t.checkResult] || '未完成';

    showModal(`
        <div class="modal-title">【${t.checkNo}】晨检详情</div>

        <!-- 区域一：基础信息 -->
        <div style="margin-bottom:16px;">
            <div style="font-size:13px;font-weight:600;color:#409eff;padding:6px 12px;background:#ecf5ff;border-radius:4px;margin-bottom:10px;border-left:3px solid #409eff;">
                📋 基础信息
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_hcInfoRow('晨检任务编号', t.checkNo)}
                ${_hcInfoRow('员工编号',     t.employeeNo)}
                ${_hcInfoRow('员工姓名',     t.employeeName)}
                ${_hcInfoRow('所属组织',     t.orgName)}
                ${_hcInfoRow('所属岗位',     HC_POSITION_MAP[t.position]||t.position)}
                ${_hcInfoRow('晨检日期',     t.checkDate)}
                ${_hcInfoRow('应晨检开始',   t.checkTimeStart||'—')}
                ${_hcInfoRow('应晨检截止',   t.checkTimeEnd  ||'—')}
            </div>
        </div>

        <!-- 区域二：人脸核验 -->
        <div style="margin-bottom:16px;">
            <div style="font-size:13px;font-weight:600;color:#67c23a;padding:6px 12px;background:#f0f9eb;border-radius:4px;margin-bottom:10px;border-left:3px solid #67c23a;">
                🤖 人脸核验信息
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_hcInfoRow('核验状态',
                    `<span class="tag ${fs.cls}">${fs.label}</span>`)}
                ${_hcInfoRow('核验时间',
                    t.faceVerifyTime || '—')}
                ${_hcInfoRow('匹配置信度',
                    t.faceMatchScore !== null ? t.faceMatchScore + '%' : '—')}
                ${_hcInfoRow('核验照片',
                    t.faceImageUrl
                        ? `<img src="${t.faceImageUrl}" style="width:60px;height:60px;border-radius:4px;object-fit:cover;">`
                        : `<span style="color:#c0c4cc;font-size:12px;border:1px dashed #dcdfe6;padding:4px 10px;border-radius:4px;">未上传</span>`)}
            </div>
        </div>

        <!-- 区域三：健康状态 -->
        <div style="margin-bottom:8px;">
            <div style="font-size:13px;font-weight:600;color:#e6a23c;padding:6px 12px;background:#fdf6ec;border-radius:4px;margin-bottom:10px;border-left:3px solid #e6a23c;">
                🩺 健康状态信息
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_hcInfoRow('体温（℃）',
                    t.temperature !== null ? t.temperature + '℃' : '—')}
                ${_hcInfoRow('是否发热',
                    t.hasFever     ? '<span style="color:#f56c6c;font-weight:600;">是</span>' : '否')}
                ${_hcInfoRow('是否咳嗽',
                    t.hasCough     ? '<span style="color:#f56c6c;font-weight:600;">是</span>' : '否')}
                ${_hcInfoRow('是否皮肤病',
                    t.hasSkinDisease ? '<span style="color:#f56c6c;font-weight:600;">是</span>' : '否')}
                ${_hcInfoRow('手部卫生',
                    t.handHygiene === 'pass' ? '<span style="color:#67c23a;">合格</span>'
                    : t.handHygiene === 'fail' ? '<span style="color:#f56c6c;">不合格</span>'
                    : '—')}
                ${_hcInfoRow('着装规范',
                    t.uniformCheck === 'pass' ? '<span style="color:#67c23a;">合格</span>'
                    : t.uniformCheck === 'fail' ? '<span style="color:#f56c6c;">不合格</span>'
                    : '—')}
                ${_hcInfoRow('晨检结果',
                    `<span style="font-weight:600;color:${res==='合格'?'#67c23a':res==='不合格'?'#f56c6c':'#909399'}">${res}</span>`)}
                ${_hcInfoRow('晨检状态',
                    `<span class="tag ${st.cls}">${st.label}</span>`)}
                ${_hcInfoRow('晨检人员',
                    t.checkerName || '—')}
                ${_hcInfoRow('晨检完成时间',
                    t.checkFinishTime || '—')}
            </div>
            ${t.remark ? `<div style="margin-top:10px;font-size:13px;"><span style="color:#909399;">备注：</span><span>${t.remark}</span></div>` : ''}
        </div>

        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '580px');
}

/* ============================================================
   工具函数
   ============================================================ */
function _hcFmtDate(d) {
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function _hcInfoRow(label, value) {
    return `<div style="display:flex;gap:4px;font-size:13px;align-items:flex-start;">
        <span style="color:#909399;white-space:nowrap;min-width:90px;">${label}：</span>
        <span style="color:#303133;">${value}</span>
    </div>`;
}
