/**
 * sample.js — 留样管理模块
 *
 * 功能：
 *  1. 生成留样任务（从烹饪任务生成）
 *  2. 查看留样任务列表（筛选、分页）
 *  3. 查看留样任务详情
 *  4. 生成销样任务（待销样→已销样）
 *  5. 查看销样任务详情
 */

/* ============================================================
   常量
   ============================================================ */
const SP_STATUS_MAP = {
    pending:  { label: '待销样', cls: 'tag-primary' },
    disposed: { label: '已销样', cls: 'tag-success' },
    expired:  { label: '已过期', cls: 'tag-danger'  }
};

const DS_REASON_MAP = {
    normal:   '正常销样',
    expired:  '过期销样',
    abnormal: '异常销毁'
};

const MEAL_TIME_MAP = {
    breakfast: '早餐',
    lunch:     '午餐',
    dinner:    '晚餐'
};

/* ============================================================
   全局状态
   ============================================================ */
window.spActiveTab      = 'sample';    // 'sample' | 'dispose'
window.spPage           = 1;
window.spPageSize       = 10;
window.spFilter         = { keyword: '', startDate: '', endDate: '', status: '' };
window.dsPage           = 1;
window.dsPageSize       = 10;
window.dsFilter         = { keyword: '', startDate: '', endDate: '' };

/* ============================================================
   主入口
   ============================================================ */
function renderSamplePage(container) {
    window.spActiveTab = window.spActiveTab || 'sample';
    window.spPage      = 1;
    window.dsPage      = 1;

    container.innerHTML = `
        <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
            <div class="tab-bar" style="display:flex;gap:0;border:1px solid #dcdfe6;border-radius:4px;overflow:hidden;">
                <div id="spTabSample"
                     onclick="spSwitchTab('sample')"
                     class="tab-btn${window.spActiveTab==='sample'?' tab-btn-active':''}"
                     style="padding:8px 24px;cursor:pointer;font-size:14px;background:${window.spActiveTab==='sample'?'#409eff':'#fff'};color:${window.spActiveTab==='sample'?'#fff':'#606266'};border:none;transition:all .2s;">
                    🧪 留样任务
                </div>
                <div id="spTabDispose"
                     onclick="spSwitchTab('dispose')"
                     class="tab-btn${window.spActiveTab==='dispose'?' tab-btn-active':''}"
                     style="padding:8px 24px;cursor:pointer;font-size:14px;background:${window.spActiveTab==='dispose'?'#409eff':'#fff'};color:${window.spActiveTab==='dispose'?'#fff':'#606266'};border:none;border-left:1px solid #dcdfe6;transition:all .2s;">
                    ♻️ 销样任务
                </div>
            </div>
            <button class="btn btn-primary" onclick="openGenSampleModal()">＋ 生成留样任务</button>
        </div>
        <div id="spTabContent"></div>`;

    _spRenderTab();
}

function spSwitchTab(tab) {
    window.spActiveTab = tab;
    renderSamplePage(document.getElementById('mainContent'));
}

/* ============================================================
   Tab 内容渲染
   ============================================================ */
function _spRenderTab() {
    const el = document.getElementById('spTabContent');
    if (!el) return;
    if (window.spActiveTab === 'sample') {
        _renderSampleTab(el);
    } else {
        _renderDisposeTab(el);
    }
}

/* -------------------- 留样任务 Tab -------------------- */
function _renderSampleTab(container) {
    const f = window.spFilter;
    container.innerHTML = `
        <div class="filter-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
            <input class="filter-input" placeholder="留样编号/菜品名称" value="${f.keyword}"
                   oninput="window.spFilter.keyword=this.value" style="width:200px;">
            <input type="date" class="filter-input" value="${f.startDate}"
                   oninput="window.spFilter.startDate=this.value" title="留样开始日期" style="width:150px;">
            <span style="color:#606266;font-size:13px;">至</span>
            <input type="date" class="filter-input" value="${f.endDate}"
                   oninput="window.spFilter.endDate=this.value" title="留样结束日期" style="width:150px;">
            <select class="filter-input" style="width:120px;" onchange="window.spFilter.status=this.value">
                <option value="" ${f.status===''?'selected':''}>全部状态</option>
                <option value="pending"  ${f.status==='pending' ?'selected':''}>待销样</option>
                <option value="disposed" ${f.status==='disposed'?'selected':''}>已销样</option>
                <option value="expired"  ${f.status==='expired' ?'selected':''}>已过期</option>
            </select>
            <button class="btn btn-primary" onclick="spSearchSample()">🔍 查询</button>
            <button class="btn btn-default" onclick="spResetSampleFilter()">↺ 重置</button>
        </div>
        <div id="spSampleTableWrap"></div>`;

    _renderSampleRows();
}

function spSearchSample() {
    window.spPage = 1;
    _renderSampleRows();
}

function spResetSampleFilter() {
    window.spFilter = { keyword: '', startDate: '', endDate: '', status: '' };
    window.spPage = 1;
    _renderSampleTab(document.getElementById('spTabContent'));
}

function _filterSample() {
    const f = window.spFilter;
    return (window.mockData.sampleTasks || []).filter(s => {
        if (f.keyword) {
            const kw = f.keyword.toLowerCase();
            if (!s.sampleNo.toLowerCase().includes(kw) && !s.dishName.toLowerCase().includes(kw)) return false;
        }
        if (f.startDate && s.sampleTime.slice(0, 10) < f.startDate) return false;
        if (f.endDate   && s.sampleTime.slice(0, 10) > f.endDate)   return false;
        if (f.status && s.status !== f.status) return false;
        return true;
    });
}

function _renderSampleRows() {
    const wrap = document.getElementById('spSampleTableWrap');
    if (!wrap) return;

    const list    = _filterSample();
    const total   = list.length;
    const ps      = window.spPageSize;
    const page    = window.spPage;
    const pages   = Math.max(1, Math.ceil(total / ps));
    const start   = (page - 1) * ps;
    const pageData = list.slice(start, start + ps);

    let rows = '';
    if (pageData.length === 0) {
        rows = `<tr><td colspan="9" style="text-align:center;color:#999;padding:32px;">暂无数据</td></tr>`;
    } else {
        pageData.forEach(s => {
            const st   = SP_STATUS_MAP[s.status] || { label: s.status, cls: 'tag-info' };
            const now  = new Date();
            const exp  = new Date(s.expireTime);
            const isExpiring = s.status === 'pending' && (exp - now) < 24 * 3600 * 1000 && (exp - now) > 0;
            rows += `<tr>
                <td>${s.sampleNo}</td>
                <td>${s.dishName}</td>
                <td>${s.sampleWeight}g</td>
                <td>${s.sampleTime}</td>
                <td>${s.storageLocation}</td>
                <td>${s.samplerName}</td>
                <td style="color:${isExpiring?'#e6a23c':'inherit'}">${s.expireTime}${isExpiring?' ⚠️':''}</td>
                <td><span class="tag ${st.cls}">${st.label}</span></td>
                <td>
                    <button class="btn btn-info btn-sm" onclick="showSampleDetail(${s.id})">详情</button>
                    ${s.status==='pending'?`<button class="btn btn-warning btn-sm" onclick="openGenDisposeModal(${s.id})">销样</button>`:''}
                </td>
            </tr>`;
        });
    }

    wrap.innerHTML = `
        <table class="data-table">
            <thead>
                <tr>
                    <th>留样编号</th>
                    <th>菜品名称</th>
                    <th>留样重量</th>
                    <th>留样时间</th>
                    <th>存放位置</th>
                    <th>留样人</th>
                    <th>过期时间</th>
                    <th>状态</th>
                    <th style="width:140px;">操作</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
        <div class="pagination" style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="spSamplePaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="spSamplePaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function spSamplePaginate(p) {
    const total = _filterSample().length;
    const pages = Math.max(1, Math.ceil(total / window.spPageSize));
    window.spPage = Math.max(1, Math.min(p, pages));
    _renderSampleRows();
}

/* -------------------- 销样任务 Tab -------------------- */
function _renderDisposeTab(container) {
    const f = window.dsFilter;
    container.innerHTML = `
        <div class="filter-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
            <input class="filter-input" placeholder="销样编号/菜品名称" value="${f.keyword}"
                   oninput="window.dsFilter.keyword=this.value" style="width:200px;">
            <input type="date" class="filter-input" value="${f.startDate}"
                   oninput="window.dsFilter.startDate=this.value" title="销样开始日期" style="width:150px;">
            <span style="color:#606266;font-size:13px;">至</span>
            <input type="date" class="filter-input" value="${f.endDate}"
                   oninput="window.dsFilter.endDate=this.value" title="销样结束日期" style="width:150px;">
            <button class="btn btn-primary" onclick="dsSearchDispose()">🔍 查询</button>
            <button class="btn btn-default" onclick="dsResetFilter()">↺ 重置</button>
        </div>
        <div id="dsDisposeTableWrap"></div>`;

    _renderDisposeRows();
}

function dsSearchDispose() {
    window.dsPage = 1;
    _renderDisposeRows();
}

function dsResetFilter() {
    window.dsFilter = { keyword: '', startDate: '', endDate: '' };
    window.dsPage = 1;
    _renderDisposeTab(document.getElementById('spTabContent'));
}

function _filterDispose() {
    const f = window.dsFilter;
    return (window.mockData.disposeTasks || []).filter(d => {
        if (f.keyword) {
            const kw = f.keyword.toLowerCase();
            if (!d.disposeNo.toLowerCase().includes(kw) && !d.dishName.toLowerCase().includes(kw)) return false;
        }
        if (f.startDate && d.disposeTime.slice(0, 10) < f.startDate) return false;
        if (f.endDate   && d.disposeTime.slice(0, 10) > f.endDate)   return false;
        return true;
    });
}

function _renderDisposeRows() {
    const wrap = document.getElementById('dsDisposeTableWrap');
    if (!wrap) return;

    const list     = _filterDispose();
    const total    = list.length;
    const ps       = window.dsPageSize;
    const page     = window.dsPage;
    const pages    = Math.max(1, Math.ceil(total / ps));
    const start    = (page - 1) * ps;
    const pageData = list.slice(start, start + ps);

    let rows = '';
    if (pageData.length === 0) {
        rows = `<tr><td colspan="8" style="text-align:center;color:#999;padding:32px;">暂无数据</td></tr>`;
    } else {
        pageData.forEach(d => {
            const reason = DS_REASON_MAP[d.disposeReason] || d.disposeReason;
            rows += `<tr>
                <td>${d.disposeNo}</td>
                <td>${d.sampleNo}</td>
                <td>${d.dishName}</td>
                <td>${reason}</td>
                <td>${d.disposeTime}</td>
                <td>${d.disposerName}</td>
                <td><span class="tag tag-success">已完成</span></td>
                <td>
                    <button class="btn btn-info btn-sm" onclick="showDisposeDetail(${d.id})">详情</button>
                </td>
            </tr>`;
        });
    }

    wrap.innerHTML = `
        <table class="data-table">
            <thead>
                <tr>
                    <th>销样编号</th>
                    <th>关联留样编号</th>
                    <th>菜品名称</th>
                    <th>销样原因</th>
                    <th>销样时间</th>
                    <th>操作人</th>
                    <th>状态</th>
                    <th style="width:80px;">操作</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
        <div class="pagination" style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="dsDisposePaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="dsDisposePaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function dsDisposePaginate(p) {
    const total = _filterDispose().length;
    const pages = Math.max(1, Math.ceil(total / window.dsPageSize));
    window.dsPage = Math.max(1, Math.min(p, pages));
    _renderDisposeRows();
}

/* ============================================================
   生成留样任务弹窗
   ============================================================ */
function openGenSampleModal() {
    const today = new Date().toISOString().slice(0, 10);
    showModal(`
        <div class="modal-title">生成留样任务</div>
        <div style="margin-bottom:16px;">
            <div style="margin-bottom:8px;color:#606266;font-size:13px;">选择烹饪日期，从当日已完成的烹饪任务自动生成留样记录</div>
            <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
                <label style="font-size:14px;font-weight:600;color:#303133;">烹饪日期：</label>
                <input type="date" id="genSampleDate" class="filter-input" value="${today}" style="width:180px;">
                <button class="btn btn-primary" onclick="_previewGenSample()">预览可生成任务</button>
            </div>
            <div id="genSamplePreview" style="min-height:60px;"></div>
        </div>
        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" id="btnConfirmGenSample" onclick="confirmGenSample()" disabled>确认生成</button>
        </div>
    `, '560px');

    // 默认预览今日
    _previewGenSample();
}

window._genSampleTargets = [];

function _previewGenSample() {
    const date = document.getElementById('genSampleDate');
    if (!date) return;
    const d = date.value;
    if (!d) {
        document.getElementById('genSamplePreview').innerHTML = '<span style="color:#e6a23c;">请选择日期</span>';
        return;
    }

    // 找当日已完成烹饪任务
    const doneTasks = (window.mockData.cookTasks || []).filter(t => t.cookDate === d && t.status === 'done');
    // 排除已有留样的
    const existNos = new Set((window.mockData.sampleTasks || []).map(s => s.cookTaskId));
    const newTasks = doneTasks.filter(t => !existNos.has(t.id));

    window._genSampleTargets = newTasks;

    const btn = document.getElementById('btnConfirmGenSample');
    if (btn) btn.disabled = newTasks.length === 0;

    if (doneTasks.length === 0) {
        document.getElementById('genSamplePreview').innerHTML = `<div style="color:#909399;font-size:13px;padding:12px 0;">该日期暂无已完成烹饪任务</div>`;
        return;
    }
    if (newTasks.length === 0) {
        document.getElementById('genSamplePreview').innerHTML = `<div style="color:#67c23a;font-size:13px;padding:12px 0;">✅ 该日期所有已完成烹饪任务均已生成留样，无需重复生成</div>`;
        return;
    }

    let rows = newTasks.map(t => `
        <tr>
            <td>${t.taskNo}</td>
            <td>${t.recipeName}</td>
            <td>${MEAL_TIME_MAP[t.mealTime]||t.mealTime}</td>
            <td>${t.chefName}</td>
        </tr>`).join('');

    document.getElementById('genSamplePreview').innerHTML = `
        <div style="margin-bottom:8px;color:#409eff;font-size:13px;">以下 <b>${newTasks.length}</b> 条烹饪任务可生成留样：</div>
        <table class="data-table" style="font-size:13px;">
            <thead><tr><th>任务编号</th><th>菜品名称</th><th>餐次</th><th>厨师</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>`;
}

function confirmGenSample() {
    const targets = window._genSampleTargets || [];
    if (targets.length === 0) {
        showToast('没有可生成的留样任务', 'error');
        return;
    }

    const now = new Date();
    const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
    const nowStr  = _spFormatDate(now);
    // 过期时间：留样后48小时
    const expire  = new Date(now.getTime() + 48 * 3600 * 1000);

    const existing = window.mockData.sampleTasks || [];
    const maxId    = existing.reduce((m, s) => Math.max(m, s.id), 0);

    targets.forEach((t, i) => {
        const id = maxId + i + 1;
        const seq = String(existing.length + i + 1).padStart(3, '0');
        existing.push({
            id:              id,
            sampleNo:        `SP-${dateStr}-${seq}`,
            cookTaskId:      t.id,
            cookTaskNo:      t.taskNo,
            dishName:        t.recipeName,
            sampleWeight:    125,
            sampleTime:      nowStr,
            samplerId:       1,
            samplerName:     '赵厨长',
            storageLocation: window.mockData.sampleCabinets[i % 5].name,
            qualityScore:    null,
            status:          'pending',
            photoUrl:        '',
            remark:          '',
            expireTime:      _spFormatDate(expire),
            tenantId:        1,
            createdAt:       nowStr,
            updatedAt:       nowStr
        });
    });

    window.mockData.sampleTasks = existing;
    closeModal();
    showToast(`成功生成 ${targets.length} 条留样任务`);
    window.spActiveTab = 'sample';
    renderSamplePage(document.getElementById('mainContent'));
}

/* ============================================================
   留样详情弹窗
   ============================================================ */
function showSampleDetail(id) {
    const s = (window.mockData.sampleTasks || []).find(x => x.id === id);
    if (!s) { showToast('数据不存在', 'error'); return; }

    const st       = SP_STATUS_MAP[s.status] || { label: s.status, cls: 'tag-info' };
    const cookTask = (window.mockData.cookTasks || []).find(t => t.id === s.cookTaskId);
    const disposeTask = (window.mockData.disposeTasks || []).find(d => d.sampleId === s.id);

    showModal(`
        <div class="modal-title">留样详情</div>
        <div style="border-bottom:1px solid #ebeef5;padding-bottom:12px;margin-bottom:16px;display:flex;align-items:center;gap:12px;">
            <span style="font-size:15px;font-weight:600;color:#303133;">${s.sampleNo}</span>
            <span class="tag ${st.cls}">${st.label}</span>
        </div>

        <div style="margin-bottom:16px;">
            <div class="section-title" style="font-size:13px;font-weight:600;color:#909399;margin-bottom:10px;">基础信息</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_spInfoRow('菜品名称', s.dishName)}
                ${_spInfoRow('留样重量', s.sampleWeight + 'g')}
                ${_spInfoRow('留样时间', s.sampleTime)}
                ${_spInfoRow('过期时间', s.expireTime)}
                ${_spInfoRow('存放位置', s.storageLocation)}
                ${_spInfoRow('留样人', s.samplerName)}
                ${_spInfoRow('品质评分', s.qualityScore !== null ? s.qualityScore + '分' : '—')}
                ${_spInfoRow('备注', s.remark || '—')}
            </div>
        </div>

        ${cookTask ? `
        <div style="margin-bottom:16px;">
            <div class="section-title" style="font-size:13px;font-weight:600;color:#909399;margin-bottom:10px;">关联烹饪任务</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_spInfoRow('任务编号', cookTask.taskNo)}
                ${_spInfoRow('餐次', MEAL_TIME_MAP[cookTask.mealTime]||cookTask.mealTime)}
                ${_spInfoRow('厨师', cookTask.chefName)}
                ${_spInfoRow('烹饪日期', cookTask.cookDate)}
            </div>
        </div>` : ''}

        ${disposeTask ? `
        <div style="margin-bottom:8px;">
            <div class="section-title" style="font-size:13px;font-weight:600;color:#909399;margin-bottom:10px;">销样信息</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_spInfoRow('销样编号', disposeTask.disposeNo)}
                ${_spInfoRow('销样原因', DS_REASON_MAP[disposeTask.disposeReason]||disposeTask.disposeReason)}
                ${_spInfoRow('销样时间', disposeTask.disposeTime)}
                ${_spInfoRow('操作人', disposeTask.disposerName)}
                ${_spInfoRow('备注', disposeTask.remark || '—')}
            </div>
        </div>` : ''}

        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
            ${s.status==='pending'?`<button class="btn btn-warning" onclick="closeModal();openGenDisposeModal(${s.id})">生成销样</button>`:''}
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '560px');
}

/* ============================================================
   生成销样任务弹窗
   ============================================================ */
function openGenDisposeModal(sampleId) {
    const s = (window.mockData.sampleTasks || []).find(x => x.id === sampleId);
    if (!s) { showToast('留样记录不存在', 'error'); return; }
    if (s.status !== 'pending') { showToast('只有待销样状态的记录才能生成销样任务', 'error'); return; }

    const isExpired = new Date() > new Date(s.expireTime);
    const defaultReason = isExpired ? 'expired' : 'normal';

    showModal(`
        <div class="modal-title">生成销样任务</div>
        <div style="background:#fef9f0;border:1px solid #faecd8;border-radius:4px;padding:10px 14px;margin-bottom:16px;font-size:13px;color:#e6a23c;">
            ⚠️ 生成销样后，留样状态将更新为「已销样」，操作不可撤销，请谨慎确认。
        </div>
        <div style="display:grid;grid-template-columns:100px 1fr;gap:12px;align-items:center;margin-bottom:16px;">
            <label style="font-size:14px;color:#606266;text-align:right;">留样编号：</label>
            <span style="font-size:14px;font-weight:600;">${s.sampleNo}</span>

            <label style="font-size:14px;color:#606266;text-align:right;">菜品名称：</label>
            <span style="font-size:14px;">${s.dishName}</span>

            <label style="font-size:14px;color:#606266;text-align:right;">存放位置：</label>
            <span style="font-size:14px;">${s.storageLocation}</span>

            <label style="font-size:14px;color:#606266;text-align:right;">过期时间：</label>
            <span style="font-size:14px;color:${isExpired?'#f56c6c':'#303133'};">${s.expireTime}${isExpired?' （已过期）':''}</span>

            <label style="font-size:14px;color:#606266;text-align:right;"><span style="color:#f56c6c;">*</span> 销样原因：</label>
            <select id="disposeReasonSel" class="filter-input" style="width:180px;">
                <option value="normal"   ${defaultReason==='normal'  ?'selected':''}>正常销样</option>
                <option value="expired"  ${defaultReason==='expired' ?'selected':''}>过期销样</option>
                <option value="abnormal"                                             >异常销毁</option>
            </select>

            <label style="font-size:14px;color:#606266;text-align:right;">备注：</label>
            <textarea id="disposeRemark" rows="2" class="filter-input" style="width:100%;resize:none;" placeholder="可选"></textarea>
        </div>
        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-warning" onclick="confirmGenDispose(${s.id})">确认生成销样</button>
        </div>
    `, '500px');
}

function confirmGenDispose(sampleId) {
    const s = (window.mockData.sampleTasks || []).find(x => x.id === sampleId);
    if (!s) { showToast('数据不存在', 'error'); return; }

    const reasonSel = document.getElementById('disposeReasonSel');
    const remarkEl  = document.getElementById('disposeRemark');
    if (!reasonSel) return;

    const reason = reasonSel.value;
    const remark = remarkEl ? remarkEl.value.trim() : '';

    const now    = new Date();
    const nowStr = _spFormatDate(now);
    const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');

    const dt      = window.mockData.disposeTasks || [];
    const maxId   = dt.reduce((m, d) => Math.max(m, d.id), 0);
    const seq     = String(dt.length + 1).padStart(3, '0');

    dt.push({
        id:           maxId + 1,
        disposeNo:    `DS-${dateStr}-${seq}`,
        sampleId:     s.id,
        sampleNo:     s.sampleNo,
        dishName:     s.dishName,
        disposeTime:  nowStr,
        disposerId:   1,
        disposerName: '赵厨长',
        disposeReason: reason,
        status:       'done',
        remark:       remark,
        tenantId:     1,
        createdAt:    nowStr,
        updatedAt:    nowStr
    });
    window.mockData.disposeTasks = dt;

    // 更新留样状态
    s.status    = 'disposed';
    s.updatedAt = nowStr;

    closeModal();
    showToast('销样任务已生成');
    window.spActiveTab = 'dispose';
    renderSamplePage(document.getElementById('mainContent'));
}

/* ============================================================
   销样详情弹窗
   ============================================================ */
function showDisposeDetail(id) {
    const d = (window.mockData.disposeTasks || []).find(x => x.id === id);
    if (!d) { showToast('数据不存在', 'error'); return; }

    const sample = (window.mockData.sampleTasks || []).find(s => s.id === d.sampleId);
    const reason = DS_REASON_MAP[d.disposeReason] || d.disposeReason;

    showModal(`
        <div class="modal-title">销样详情</div>
        <div style="border-bottom:1px solid #ebeef5;padding-bottom:12px;margin-bottom:16px;display:flex;align-items:center;gap:12px;">
            <span style="font-size:15px;font-weight:600;color:#303133;">${d.disposeNo}</span>
            <span class="tag tag-success">已完成</span>
        </div>

        <div style="margin-bottom:16px;">
            <div class="section-title" style="font-size:13px;font-weight:600;color:#909399;margin-bottom:10px;">销样信息</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_spInfoRow('关联留样编号', d.sampleNo)}
                ${_spInfoRow('菜品名称', d.dishName)}
                ${_spInfoRow('销样原因', reason)}
                ${_spInfoRow('销样时间', d.disposeTime)}
                ${_spInfoRow('操作人', d.disposerName)}
                ${_spInfoRow('备注', d.remark || '—')}
            </div>
        </div>

        ${sample ? `
        <div style="margin-bottom:8px;">
            <div class="section-title" style="font-size:13px;font-weight:600;color:#909399;margin-bottom:10px;">关联留样信息</div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;">
                ${_spInfoRow('留样编号', sample.sampleNo)}
                ${_spInfoRow('留样重量', sample.sampleWeight + 'g')}
                ${_spInfoRow('留样时间', sample.sampleTime)}
                ${_spInfoRow('过期时间', sample.expireTime)}
                ${_spInfoRow('存放位置', sample.storageLocation)}
                ${_spInfoRow('留样人', sample.samplerName)}
            </div>
        </div>` : ''}

        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '560px');
}

/* ============================================================
   工具函数
   ============================================================ */
function _spFormatDate(d) {
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function _spInfoRow(label, value) {
    return `<div style="display:flex;gap:4px;font-size:13px;align-items:flex-start;">
        <span style="color:#909399;white-space:nowrap;min-width:80px;">${label}：</span>
        <span style="color:#303133;">${value}</span>
    </div>`;
}
