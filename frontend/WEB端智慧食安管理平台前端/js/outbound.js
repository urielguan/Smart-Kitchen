/**
 * outbound.js — 出库管理
 *
 * ===== 运行验证说明 =====
 *  双击 index.html → 点击左侧「仓储管理 / 出库管理」导航进入版块
 *
 *  【① 新增出库单】
 *  - 点击右上角「新增出库单」→ 弹窗填写：出库类型（领料/调拨/报废）、关联单据号（可选）、
 *    出库仓库（必填）、领用部门（必填）、出库日期（必填）、操作人、备注
 *  - 物料清单：至少1行；物料下拉→规格联动→单位自动带出→填写数量/出库原因/批次号
 *  - 提交后弹窗关闭，列表新增，状态默认「待审核」，提示"出库单新增成功"
 *
 *  【② 列表筛选/分页】
 *  - 筛选：出库单号/出库类型/出库仓库/日期范围/状态（含已删除）
 *  - 分页10条，默认隐藏已删除单据（通过状态筛选可查找）
 *
 *  【③ 查看详情】
 *  - 点击任意行「详情」或出库单号链接 → 只读弹窗，含基本信息+审核信息（如有）+物料清单
 *
 *  【④ 编辑出库单】
 *  - 仅「待审核」可编辑，其他状态置灰提示；编辑提交后列表实时更新，提示"出库单编辑成功"
 *
 *  【⑤ 审核出库单】
 *  - 仅「待审核」可审核；弹窗含审核意见输入框
 *  - 审核通过 → 状态「已审核」；审核驳回 → 状态「已作废」
 *
 *  【⑥ 删除出库单】
 *  - 「待审核」「已作废」可删除；「已审核」置灰提示"已审核的出库单不允许删除"
 *  - 二次确认弹窗后软删除（状态→已删除）
 *
 * ===== 核心改动点 =====
 *  1. 新建 js/outbound.js（本文件）
 *  2. mock-data.js 新增 outboundOrders 数组（5条 Mock 出库单）
 *  3. sidebar.js menuConfig「仓储管理」组追加 { label:'出库管理', page:'outbound', icon:'📤' }
 *  4. sidebar.js renderPage() 追加 outbound → renderOutboundPage() 分支
 *  5. index.html 追加 <script src="js/outbound.js">
 */

/* ============================================================
   常量
   ============================================================ */
const OB_STATUS_MAP = {
    pending:  { label: '待审核', cls: 'tag-warning' },
    approved: { label: '已审核', cls: 'tag-success'  },
    void:     { label: '已作废', cls: 'tag-info'     },
    deleted:  { label: '已删除', cls: 'tag-danger'   }
};

const OB_TYPE_MAP = {
    pick:     '领料',
    transfer: '调拨',
    scrap:    '报废'
};

const OB_OUT_REASON_MAP = {
    normal: '正常领用',
    expire: '临期处理',
    scrap:  '报废'
};

/* ============================================================
   状态变量
   ============================================================ */
window.obPage         = 1;
window.obPageSize     = 10;
window.obFilteredList = [];
window._obRowIdx      = 0;

/* ============================================================
   渲染入口
   ============================================================ */
function renderOutboundPage(container) {
    const whNames = [...new Set((window.mockData.warehouses || []).map(w => w.warehouseName))];
    const whOpts  = whNames.map(w => `<option value="${w}">${w}</option>`).join('');

    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="obSearchNo"   placeholder="出库单号"   style="width:160px">
                <select class="form-select" id="obSearchType" style="width:110px">
                    <option value="">全部类型</option>
                    <option value="pick">领料</option>
                    <option value="transfer">调拨</option>
                    <option value="scrap">报废</option>
                </select>
                <select class="form-select" id="obSearchWh" style="width:150px">
                    <option value="">全部仓库</option>
                    ${whOpts}
                </select>
                <input type="date" class="form-input" id="obSearchDateFrom" style="width:140px" title="出库日期起">
                <input type="date" class="form-input" id="obSearchDateTo"   style="width:140px" title="出库日期止">
                <select class="form-select" id="obSearchStatus" style="width:110px">
                    <option value="">全部状态</option>
                    <option value="pending">待审核</option>
                    <option value="approved">已审核</option>
                    <option value="void">已作废</option>
                    <option value="deleted">已删除</option>
                </select>
                <button class="btn btn-primary" onclick="searchOutbound()">搜索</button>
                <button class="btn btn-default" onclick="resetOutboundSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openOutboundModal()">＋ 新增出库单</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>出库单号</th>
                        <th>出库类型</th>
                        <th>关联单据</th>
                        <th>出库仓库</th>
                        <th>领用部门</th>
                        <th>出库日期</th>
                        <th>物料种数</th>
                        <th>操作人</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="obTableBody"></tbody>
            </table>
            <div class="pagination" id="obPagination"></div>
        </div>`;

    _filterOutbound();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterOutbound() {
    const no       = (document.getElementById('obSearchNo')?.value      || '').trim().toLowerCase();
    const type     = (document.getElementById('obSearchType')?.value    || '');
    const wh       = (document.getElementById('obSearchWh')?.value      || '');
    const status   = (document.getElementById('obSearchStatus')?.value  || '');
    const dateFrom = (document.getElementById('obSearchDateFrom')?.value || '');
    const dateTo   = (document.getElementById('obSearchDateTo')?.value   || '');

    window.obFilteredList = (window.mockData.outboundOrders || []).filter(o => {
        if (!status && o.status === 'deleted')                            return false;
        if (no     && !o.orderNo.toLowerCase().includes(no))             return false;
        if (type   && o.outboundType !== type)                           return false;
        if (wh     && o.warehouseName !== wh)                            return false;
        if (status && o.status !== status)                               return false;
        if (dateFrom && o.outboundDate < dateFrom)                       return false;
        if (dateTo   && o.outboundDate > dateTo)                         return false;
        return true;
    });

    window.obPage = 1;
    _renderOutboundRows();
}

function searchOutbound()      { _filterOutbound(); }
function resetOutboundSearch() {
    ['obSearchNo','obSearchType','obSearchWh','obSearchStatus','obSearchDateFrom','obSearchDateTo']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterOutbound();
}

function obChangePage(p) { window.obPage = p; _renderOutboundRows(); }

function _renderOutboundRows() {
    const list     = window.obFilteredList;
    const total    = list.length;
    const pageSize = window.obPageSize;
    const page     = window.obPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('obTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:40px;color:#909399">暂无出库单数据</td></tr>`;
        document.getElementById('obPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(o => {
        const sm       = OB_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
        const matCount = (o.items || []).length;
        const typeLabel = OB_TYPE_MAP[o.outboundType] || o.outboundType || '—';

        // 编辑：仅待审核
        const canEdit = o.status === 'pending';
        const editBtn = canEdit
            ? `<button class="btn-link" onclick="openOutboundModal(${o.id})">编辑</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('仅待审核状态可编辑','error')">编辑</button>`;

        // 审核：仅待审核
        const canAudit = o.status === 'pending';
        const auditBtn = canAudit
            ? `<button class="btn-link" style="color:#409eff" onclick="openObAuditModal(${o.id})">审核</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_obAuditDisabledTip(o.status)}','error')">审核</button>`;

        // 删除：待审核/已作废
        const canDelete = o.status === 'pending' || o.status === 'void';
        const deleteBtn = canDelete
            ? `<button class="btn-link danger" onclick="deleteOutbound(${o.id})">删除</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_obDeleteDisabledTip(o.status)}','error')">删除</button>`;

        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showOutboundDetail(${o.id})">${o.orderNo}</a></td>
            <td>${typeLabel}</td>
            <td>${o.refNo || '—'}</td>
            <td>${o.warehouseName}</td>
            <td>${o.deptName || '—'}</td>
            <td>${o.outboundDate}</td>
            <td>${matCount} 种</td>
            <td>${o.operatorName}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showOutboundDetail(${o.id})">详情</button>
                    ${editBtn}
                    ${auditBtn}
                    ${deleteBtn}
                </div>
            </td>
        </tr>`;
    }).join('');

    const totalPages = Math.ceil(total / pageSize);
    let pgHtml = `<span style="color:#606266;font-size:14px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="obChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="obChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="obChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('obPagination').innerHTML = pgHtml;
}

function _obAuditDisabledTip(status) {
    if (status === 'approved') return '该出库单已审核';
    if (status === 'void')     return '该出库单已作废，无需审核';
    if (status === 'deleted')  return '已删除出库单不可操作';
    return '当前状态不可审核';
}

function _obDeleteDisabledTip(status) {
    if (status === 'approved') return '已审核的出库单不允许删除';
    if (status === 'deleted')  return '该出库单已删除';
    return '当前状态不可删除';
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showOutboundDetail(id) {
    const o = (window.mockData.outboundOrders || []).find(x => x.id === id);
    if (!o) return;
    const sm        = OB_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
    const typeLabel = OB_TYPE_MAP[o.outboundType] || o.outboundType || '—';

    const itemsHtml = (o.items || []).map(it => `
        <tr>
            <td>${it.materialName}</td>
            <td>${it.spec || '—'}</td>
            <td>${it.unit}</td>
            <td>${it.quantity}</td>
            <td>${OB_OUT_REASON_MAP[it.outReason] || it.outReason || '—'}</td>
            <td>${it.batchNo || '—'}</td>
            <td>${it.remark || '—'}</td>
        </tr>`).join('');

    const auditSection = o.auditAt ? `
        <div class="detail-section">
            <div class="detail-section-title">审核信息</div>
            <div class="detail-grid">
                <div class="detail-item"><span class="detail-label">审核结果</span>
                    <span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span>
                </div>
                <div class="detail-item"><span class="detail-label">审核时间</span>
                    <span class="detail-value">${o.auditAt}</span>
                </div>
                ${o.auditRemark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">审核意见</span><span class="detail-value">${o.auditRemark}</span></div>` : ''}
            </div>
        </div>` : '';

    const html = `
        <div class="modal-header">
            <span class="modal-title">${o.orderNo} - 出库单详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">出库单号</span><span class="detail-value">${o.orderNo}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">出库类型</span><span class="detail-value">${typeLabel}</span></div>
                    <div class="detail-item"><span class="detail-label">关联单据号</span><span class="detail-value">${o.refNo || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">出库仓库</span><span class="detail-value">${o.warehouseName}</span></div>
                    <div class="detail-item"><span class="detail-label">领用部门</span><span class="detail-value">${o.deptName || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">出库日期</span><span class="detail-value">${o.outboundDate}</span></div>
                    <div class="detail-item"><span class="detail-label">操作人</span><span class="detail-value">${o.operatorName}</span></div>
                    ${o.remark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${o.remark}</span></div>` : ''}
                </div>
            </div>
            ${auditSection}
            <div class="detail-section">
                <div class="detail-section-title">物料清单</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>物料名称</th><th>规格</th><th>单位</th>
                            <th>出库数量</th><th>出库原因</th><th>批次号</th><th>备注</th>
                        </tr></thead>
                        <tbody>${itemsHtml || '<tr><td colspan="7" style="text-align:center;color:#909399">暂无物料</td></tr>'}</tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;
    showModal(html, '820px');
}

/* ============================================================
   审核弹窗
   ============================================================ */
function openObAuditModal(id) {
    const o = (window.mockData.outboundOrders || []).find(x => x.id === id);
    if (!o) return;
    if (o.status !== 'pending') {
        showToast(_obAuditDisabledTip(o.status), 'error');
        return;
    }

    const typeLabel = OB_TYPE_MAP[o.outboundType] || o.outboundType || '—';
    const matCount  = (o.items || []).length;

    const html = `
        <div class="modal-header">
            <span class="modal-title">审核出库单</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="background:#f5f7fa;border-radius:6px;padding:16px;margin-bottom:20px">
                <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px 20px;font-size:14px">
                    <div><span style="color:#909399">出库单号：</span><span style="color:#303133;font-weight:500">${o.orderNo}</span></div>
                    <div><span style="color:#909399">出库类型：</span><span style="color:#303133">${typeLabel}</span></div>
                    <div><span style="color:#909399">出库仓库：</span><span style="color:#303133">${o.warehouseName}</span></div>
                    <div><span style="color:#909399">领用部门：</span><span style="color:#303133">${o.deptName || '—'}</span></div>
                    <div><span style="color:#909399">出库日期：</span><span style="color:#303133">${o.outboundDate}</span></div>
                    <div><span style="color:#909399">物料种数：</span><span style="color:#303133">${matCount} 种</span></div>
                    ${o.remark ? `<div style="grid-column:span 2"><span style="color:#909399">备注：</span><span style="color:#303133">${o.remark}</span></div>` : ''}
                </div>
            </div>
            <div class="form-item">
                <label class="form-label">审核意见</label>
                <textarea class="form-textarea" id="fObAuditRemark" rows="3"
                          placeholder="请输入审核意见（可选）" style="width:100%"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="rejectOutbound(${id})" style="margin-left:8px">审核驳回</button>
            <button class="btn btn-primary" onclick="approveOutbound(${id})" style="margin-left:8px">审核通过</button>
        </div>`;
    showModal(html, '580px');
}

function approveOutbound(id) {
    const o = (window.mockData.outboundOrders || []).find(x => x.id === id);
    if (!o) return;
    const remark  = document.getElementById('fObAuditRemark')?.value || '';
    o.status      = 'approved';
    o.auditAt     = _obNow();
    o.auditRemark = remark;
    o.updatedAt   = _obNow();
    closeModal();
    showToast('出库单审核通过');
    _filterOutbound();
}

function rejectOutbound(id) {
    const o = (window.mockData.outboundOrders || []).find(x => x.id === id);
    if (!o) return;
    const remark  = document.getElementById('fObAuditRemark')?.value || '';
    o.status      = 'void';
    o.auditAt     = _obNow();
    o.auditRemark = remark;
    o.updatedAt   = _obNow();
    closeModal();
    showToast('出库单审核驳回，状态置为作废', 'error');
    _filterOutbound();
}

/* ============================================================
   删除（软删除）
   ============================================================ */
function deleteOutbound(id) {
    const o = (window.mockData.outboundOrders || []).find(x => x.id === id);
    if (!o) return;
    if (o.status === 'approved') {
        showToast('已审核的出库单不允许删除', 'error');
        return;
    }
    if (o.status === 'deleted') {
        showToast('该出库单已删除', 'error');
        return;
    }

    const html = `
        <div class="modal-header">
            <span class="modal-title">删除出库单</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="text-align:center;padding:20px 0">
                <div style="font-size:48px;margin-bottom:12px">⚠️</div>
                <div style="font-size:16px;font-weight:500;color:#303133;margin-bottom:8px">
                    是否确认删除出库单 <span style="color:#f56c6c">${o.orderNo}</span>？
                </div>
                <div style="font-size:13px;color:#909399">
                    删除后标记为已删除，不可恢复
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="_doDeleteOutbound(${id})" style="margin-left:8px">确认删除</button>
        </div>`;
    showModal(html, '420px');
}

function _doDeleteOutbound(id) {
    const o = (window.mockData.outboundOrders || []).find(x => x.id === id);
    if (!o) return;
    o.status    = 'deleted';
    o.updatedAt = _obNow();
    closeModal();
    showToast('出库单已删除');
    _filterOutbound();
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openOutboundModal(id) {
    const isEdit = id != null;
    const o = isEdit
        ? JSON.parse(JSON.stringify((window.mockData.outboundOrders || []).find(x => x.id === id) || {}))
        : { id: null, orderNo: _genObNo(), outboundType: 'pick', refNo: '',
            warehouseId: '', warehouseName: '', deptId: '', deptName: '',
            outboundDate: _obToday(), operatorName: '张三', remark: '', status: 'pending', items: [] };

    if (isEdit && o.status !== 'pending') {
        showToast('仅待审核状态可编辑', 'error');
        return;
    }

    const whOptions = (window.mockData.warehouses || [])
        .map(w => `<option value="${w.id}" data-name="${w.warehouseName}" ${o.warehouseId==w.id?'selected':''}>${w.warehouseName}</option>`)
        .join('');

    const deptOptions = (window.mockData.orgs || [])
        .filter(g => g.status === 'active')
        .map(g => `<option value="${g.id}" data-name="${g.orgName}" ${o.deptId==g.id?'selected':''}>${g.orgName}</option>`)
        .join('');

    window._obRowIdx = 3000;

    const html = `
        <div class="modal-header">
            <span class="modal-title">${isEdit ? '编辑出库单' : '新增出库单'}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px">
                <div class="form-item">
                    <label class="form-label required">出库单号</label>
                    <input class="form-input" id="fObNo" value="${o.orderNo}" readonly
                           style="background:#f5f7fa;cursor:not-allowed">
                </div>
                <div class="form-item">
                    <label class="form-label required">出库类型</label>
                    <select class="form-select" id="fObType">
                        <option value="pick"     ${o.outboundType==='pick'    ?'selected':''}>领料</option>
                        <option value="transfer" ${o.outboundType==='transfer'?'selected':''}>调拨</option>
                        <option value="scrap"    ${o.outboundType==='scrap'   ?'selected':''}>报废</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">关联单据号</label>
                    <input class="form-input" id="fObRefNo" value="${o.refNo || ''}" placeholder="可选，填写关联单据号">
                </div>
                <div class="form-item">
                    <label class="form-label required">出库仓库</label>
                    <select class="form-select" id="fObWarehouseId">
                        <option value="">请选择仓库</option>
                        ${whOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">领用部门</label>
                    <select class="form-select" id="fObDeptId">
                        <option value="">请选择部门</option>
                        ${deptOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">出库日期</label>
                    <input type="date" class="form-input" id="fObDate" value="${o.outboundDate}">
                </div>
                <div class="form-item">
                    <label class="form-label">操作人</label>
                    <input class="form-input" id="fObOperator" value="${o.operatorName}">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fObRemark" rows="2">${o.remark || ''}</textarea>
                </div>
            </div>

            <!-- 物料清单 -->
            <div class="dynamic-table" style="margin-top:20px">
                <div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;
                            border-bottom:1px solid #ebeef5;padding-bottom:8px">
                    物料清单 <span style="color:#f56c6c;font-size:12px">*</span>
                </div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr style="background:#f5f7fa">
                            <th style="min-width:120px">物料名称</th>
                            <th style="min-width:120px">规格</th>
                            <th style="min-width:60px">单位</th>
                            <th style="min-width:80px">出库数量</th>
                            <th style="min-width:120px">出库原因</th>
                            <th style="min-width:120px">批次号</th>
                            <th style="min-width:80px">备注</th>
                            <th style="min-width:50px">操作</th>
                        </tr></thead>
                        <tbody id="obItemBody">
                            ${(o.items || []).map((it, idx) => _buildObItemRow(idx, it)).join('')}
                        </tbody>
                    </table>
                </div>
                <button class="btn btn-default add-row-btn" onclick="addObItemRow()" style="margin-top:8px">＋ 添加物料</button>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveOutbound(${id || 'null'})">提交出库</button>
        </div>`;

    showModal(html, 'min(900px, 96vw)');
    if (!(o.items || []).length) addObItemRow();
}

/* ============================================================
   物料明细行
   ============================================================ */
function _buildObItemRow(idx, it) {
    it = it || {};
    const materials  = (window.mockData.materials || []).map(m => ({
        id: m.id, name: m.materialName || m.name, unit: m.unit, spec: m.spec || ''
    }));
    const matOptions = materials.map(m =>
        `<option value="${m.id}" data-unit="${m.unit}" data-name="${m.name}" data-spec="${m.spec}"
                 ${it.materialId==m.id?'selected':''}>${m.name}</option>`
    ).join('');

    const selMat      = materials.find(m => m.id == it.materialId);
    const specList    = selMat ? selMat.spec.split(';').map(s=>s.trim()).filter(Boolean) : [];
    const specOptions = specList.length
        ? specList.map(s => `<option value="${s}" ${it.spec===s?'selected':''}>${s}</option>`).join('')
        : (it.spec ? `<option value="${it.spec}" selected>${it.spec}</option>` : '');

    return `<tr id="obRow_${idx}">
        <td>
            <select class="form-select" style="width:120px" onchange="onObMatChange(this,${idx})">
                <option value="">请选择</option>
                ${matOptions}
            </select>
        </td>
        <td>
            <select class="form-select" style="width:120px" id="obSpec_${idx}">
                <option value="">请选择规格</option>
                ${specOptions}
            </select>
        </td>
        <td>
            <input class="form-input" style="width:55px;background:#f5f7fa;cursor:not-allowed"
                   id="obUnit_${idx}" value="${it.unit||''}" readonly>
        </td>
        <td>
            <input type="number" class="form-input" style="width:76px" id="obQty_${idx}"
                   value="${it.quantity||''}" min="0.01" step="0.01" placeholder="数量">
        </td>
        <td>
            <select class="form-select" style="width:115px" id="obReason_${idx}">
                <option value="normal" ${(it.outReason||'normal')==='normal'?'selected':''}>正常领用</option>
                <option value="expire" ${it.outReason==='expire'?'selected':''}>临期处理</option>
                <option value="scrap"  ${it.outReason==='scrap' ?'selected':''}>报废</option>
            </select>
        </td>
        <td>
            <input class="form-input" style="width:115px" id="obBatch_${idx}"
                   value="${it.batchNo||''}" placeholder="批次号">
        </td>
        <td>
            <input class="form-input" style="width:76px" id="obRowRemark_${idx}"
                   value="${it.remark||''}" placeholder="备注">
        </td>
        <td>
            <button class="btn-link danger" onclick="removeObItemRow(${idx})">删除</button>
        </td>
    </tr>`;
}

function addObItemRow() {
    window._obRowIdx++;
    const tbody = document.getElementById('obItemBody');
    if (!tbody) return;
    const tr  = document.createElement('tr');
    tr.id     = `obRow_${window._obRowIdx}`;
    const tmp = document.createElement('tbody');
    tmp.innerHTML = _buildObItemRow(window._obRowIdx, {});
    const built = tmp.querySelector('tr');
    tr.innerHTML  = built ? built.innerHTML : '';
    tbody.appendChild(tr);
}

function removeObItemRow(idx) {
    const row = document.getElementById(`obRow_${idx}`);
    if (row) row.remove();
}

function onObMatChange(sel, idx) {
    const opt  = sel.options[sel.selectedIndex];
    const unit = opt.dataset.unit || '';
    const spec = opt.dataset.spec || '';

    const unitEl = document.getElementById(`obUnit_${idx}`);
    if (unitEl) unitEl.value = unit;

    const specEl = document.getElementById(`obSpec_${idx}`);
    if (specEl) {
        const matName  = opt.dataset.name || '';
        const selMat   = (window.mockData.materials || []).find(m => (m.materialName||m.name) === matName);
        const specList = selMat ? (selMat.spec||'').split(';').map(s=>s.trim()).filter(Boolean) : [];
        const opts = specList.length
            ? specList.map(s => `<option value="${s}">${s}</option>`).join('')
            : (spec ? `<option value="${spec}">${spec}</option>` : '');
        specEl.innerHTML = `<option value="">请选择规格</option>${opts}`;
    }
}

/* ============================================================
   保存
   ============================================================ */
function saveOutbound(id) {
    const orderNo     = document.getElementById('fObNo')?.value || '';
    const outType     = document.getElementById('fObType')?.value || 'pick';
    const whSel       = document.getElementById('fObWarehouseId');
    const warehouseId = whSel?.value || '';
    const deptSel     = document.getElementById('fObDeptId');
    const deptId      = deptSel?.value || '';
    const outDate     = document.getElementById('fObDate')?.value || '';

    if (!orderNo)     { showToast('出库单号不能为空', 'error'); return; }
    if (!warehouseId) { showToast('请选择出库仓库', 'error');   return; }
    if (!deptId)      { showToast('请选择领用部门', 'error');   return; }
    if (!outDate)     { showToast('请选择出库日期', 'error');   return; }

    const warehouseName = whSel.options[whSel.selectedIndex]?.dataset?.name
                       || whSel.options[whSel.selectedIndex]?.text || '';
    const deptName      = deptSel.options[deptSel.selectedIndex]?.dataset?.name
                       || deptSel.options[deptSel.selectedIndex]?.text || '';

    const refNo = document.getElementById('fObRefNo')?.value || '';

    const items = [];
    const tbody = document.getElementById('obItemBody');
    if (tbody) {
        tbody.querySelectorAll('tr').forEach(tr => {
            const idx    = tr.id.replace('obRow_', '');
            const matSel = tr.querySelector('select');
            const matId  = matSel ? matSel.value : '';
            if (!matId) return;
            const matName  = matSel.options[matSel.selectedIndex]?.dataset?.name
                          || matSel.options[matSel.selectedIndex]?.text || '';
            const specEl   = document.getElementById(`obSpec_${idx}`);
            const spec     = specEl?.value || '';
            const unit     = document.getElementById(`obUnit_${idx}`)?.value    || '';
            const qty      = parseFloat(document.getElementById(`obQty_${idx}`)?.value) || 0;
            const reason   = document.getElementById(`obReason_${idx}`)?.value  || 'normal';
            const batchNo  = document.getElementById(`obBatch_${idx}`)?.value   || '';
            const remark   = document.getElementById(`obRowRemark_${idx}`)?.value || '';
            if (!matName || qty <= 0) return;
            items.push({ materialId: Number(matId), materialName: matName, spec, unit,
                         quantity: qty, outReason: reason, batchNo, remark });
        });
    }

    if (!items.length) { showToast('请至少添加一条物料清单', 'error'); return; }

    const now = _obNow();

    if (id) {
        const record = (window.mockData.outboundOrders || []).find(o => o.id === id);
        if (record) {
            record.outboundType  = outType;
            record.refNo         = refNo;
            record.warehouseId   = Number(warehouseId);
            record.warehouseName = warehouseName;
            record.deptId        = Number(deptId);
            record.deptName      = deptName;
            record.outboundDate  = outDate;
            record.operatorName  = document.getElementById('fObOperator')?.value || '张三';
            record.remark        = document.getElementById('fObRemark')?.value   || '';
            record.items         = items;
            record.updatedAt     = now;
        }
        closeModal();
        showToast('出库单编辑成功');
    } else {
        const orders = window.mockData.outboundOrders || (window.mockData.outboundOrders = []);
        orders.push({
            id:            orders.length ? Math.max(...orders.map(o => o.id)) + 1 : 1,
            orderNo,
            outboundType:  outType,
            refNo,
            warehouseId:   Number(warehouseId),
            warehouseName,
            deptId:        Number(deptId),
            deptName,
            outboundDate:  outDate,
            operatorId:    1,
            operatorName:  document.getElementById('fObOperator')?.value || '张三',
            remark:        document.getElementById('fObRemark')?.value   || '',
            status:        'pending',
            tenantId:      1,
            createdAt:     now,
            updatedAt:     now,
            items
        });
        closeModal();
        showToast('出库单新增成功');
    }

    _filterOutbound();
}

/* ============================================================
   工具函数
   ============================================================ */
function _genObNo() {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(Math.floor(Math.random() * 900 + 100));
    return `OB-${d}-${seq}`;
}

function _obToday() { return new Date().toISOString().slice(0, 10); }

function _obNow()   { return new Date().toLocaleString('sv-SE').replace('T', ' '); }
