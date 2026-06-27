/**
 * inbound.js — 入库管理
 *
 * ===== 运行验证说明 =====
 *  1. 双击 index.html → 点击左侧「仓储管理 / 入库管理」导航进入版块
 *
 *  【① 新增入库单】
 *  - 点击右上角「新增入库单」→ 弹窗填写：入库仓库（必填）、关联单据类型（选"采购订单"后
 *    关联单据号下拉自动加载采购订单列表）、供应商、入库日期、物料清单（至少1行）
 *  - 物料行：选择物料→规格下拉联动→单位自动带出→填写数量/单价/批次号/保质期
 *  - 点击「提交入库」→ 校验通过后关闭弹窗，列表新增该条数据（状态：待审核），提示"入库单新增成功"
 *
 *  【② 列表筛选/分页】
 *  - 搜索栏支持：入库单号、供应商、仓库、日期范围、状态联合筛选
 *  - 默认不显示"已删除"状态的单据（通过状态筛选可查找）
 *  - 分页每页10条，点击页码/上下页切换
 *
 *  【③ 编辑入库单】
 *  - 「待审核」状态行点击「编辑」→ 弹窗回显完整数据含物料清单 → 修改后提交列表同步更新
 *  - 其他状态编辑按钮置灰，点击提示"仅待审核状态的入库单可编辑"
 *
 *  【④ 查看详情】
 *  - 点击任意行「详情」或入库单号链接 → 弹窗完整只读展示所有字段+物料清单+金额合计
 *
 *  【⑤ 审核入库单】
 *  - 「待审核」状态行点击「审核」→ 弹窗展示单据信息+审核意见 → 审核通过（→已审核）/ 审核驳回（→已作废）
 *  - 其他状态「审核」按钮置灰，点击提示对应原因
 *
 *  【⑥ 删除入库单】
 *  - 「待审核」「已作废」状态可删除 → 确认弹窗 → 确认后软删除（状态→已删除，从列表隐藏）
 *  - 「已审核」状态「删除」按钮置灰，点击提示"已审核入库单不可删除"
 *
 * ===== 核心改动点 =====
 *  1. IB_STATUS_MAP 新增 pending(待审核) / approved(已审核)，保留 void(已作废)，新增 deleted(已删除)
 *  2. _renderInboundRows() 新增「审核」「删除」按钮（含置灰逻辑）
 *  3. 新增 openIbAuditModal() / approveInbound() / rejectInbound()
 *  4. 新增 deleteInbound()（含确认弹窗）
 *  5. mock-data.js inboundOrders 状态值同步更新
 */

/* ============================================================
   常量
   ============================================================ */
const IB_STATUS_MAP = {
    pending:  { label: '待审核', cls: 'tag-warning' },
    approved: { label: '已审核', cls: 'tag-success'  },
    void:     { label: '已作废', cls: 'tag-info'     },
    deleted:  { label: '已删除', cls: 'tag-danger'   }
};

const IB_REF_TYPE_MAP = {
    purchase: '采购订单',
    none:     '无关联'
};

/* ============================================================
   状态变量
   ============================================================ */
window.ibPage         = 1;
window.ibPageSize     = 10;
window.ibFilteredList = [];
window._ibRowIdx      = 0;

/* ============================================================
   渲染入口
   ============================================================ */
function renderInboundPage(container) {
    const whNames = [...new Set((window.mockData.warehouses || []).map(w => w.warehouseName))];
    const whOpts  = whNames.map(w => `<option value="${w}">${w}</option>`).join('');

    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="ibSearchNo"  placeholder="入库单号"   style="width:160px">
                <input class="form-input" id="ibSearchSup" placeholder="供应商"     style="width:140px">
                <select class="form-select" id="ibSearchWh" style="width:150px">
                    <option value="">全部仓库</option>
                    ${whOpts}
                </select>
                <input type="date" class="form-input" id="ibSearchDateFrom" style="width:140px" title="入库日期起">
                <input type="date" class="form-input" id="ibSearchDateTo"   style="width:140px" title="入库日期止">
                <select class="form-select" id="ibSearchStatus" style="width:110px">
                    <option value="">全部状态</option>
                    <option value="pending">待审核</option>
                    <option value="approved">已审核</option>
                    <option value="void">已作废</option>
                    <option value="deleted">已删除</option>
                </select>
                <button class="btn btn-primary" onclick="searchInbound()">搜索</button>
                <button class="btn btn-default" onclick="resetInboundSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openInboundModal()">＋ 新增入库单</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>入库单号</th>
                        <th>关联单据</th>
                        <th>供应商</th>
                        <th>入库仓库</th>
                        <th>入库日期</th>
                        <th>物料种数</th>
                        <th>入库金额</th>
                        <th>操作人</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="ibTableBody"></tbody>
            </table>
            <div class="pagination" id="ibPagination"></div>
        </div>`;

    _filterInbound();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterInbound() {
    const no       = (document.getElementById('ibSearchNo')?.value    || '').trim().toLowerCase();
    const sup      = (document.getElementById('ibSearchSup')?.value   || '').trim().toLowerCase();
    const wh       = (document.getElementById('ibSearchWh')?.value    || '');
    const status   = (document.getElementById('ibSearchStatus')?.value || '');
    const dateFrom = (document.getElementById('ibSearchDateFrom')?.value || '');
    const dateTo   = (document.getElementById('ibSearchDateTo')?.value   || '');

    window.ibFilteredList = (window.mockData.inboundOrders || []).filter(o => {
        // 默认隐藏已删除（除非主动筛选）
        if (!status && o.status === 'deleted') return false;
        if (no       && !o.orderNo.toLowerCase().includes(no))               return false;
        if (sup      && !(o.supplierName || '').toLowerCase().includes(sup)) return false;
        if (wh       && o.warehouseName !== wh)                              return false;
        if (status   && o.status !== status)                                 return false;
        if (dateFrom && o.inboundDate < dateFrom)                            return false;
        if (dateTo   && o.inboundDate > dateTo)                              return false;
        return true;
    });

    window.ibPage = 1;
    _renderInboundRows();
}

function searchInbound()      { _filterInbound(); }
function resetInboundSearch() {
    ['ibSearchNo','ibSearchSup','ibSearchWh','ibSearchStatus','ibSearchDateFrom','ibSearchDateTo']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterInbound();
}

function ibChangePage(p) { window.ibPage = p; _renderInboundRows(); }

function _renderInboundRows() {
    const list     = window.ibFilteredList;
    const total    = list.length;
    const pageSize = window.ibPageSize;
    const page     = window.ibPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('ibTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align:center;padding:40px;color:#909399">暂无入库单数据</td></tr>`;
        document.getElementById('ibPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(o => {
        const sm       = IB_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
        const totalAmt = (o.items || []).reduce((s, it) => s + (it.quantity||0)*(it.unitPrice||0), 0);
        const matCount = (o.items || []).length;

        // 编辑：仅待审核可编辑
        const canEdit = o.status === 'pending';
        const editBtn = canEdit
            ? `<button class="btn-link" onclick="openInboundModal(${o.id})">编辑</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('仅待审核状态的入库单可编辑','error')">编辑</button>`;

        // 审核：仅待审核可点击
        const canAudit = o.status === 'pending';
        const auditBtn = canAudit
            ? `<button class="btn-link" style="color:#409eff" onclick="openIbAuditModal(${o.id})">审核</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_ibAuditDisabledTip(o.status)}','error')">审核</button>`;

        // 删除：待审核/已作废可删除，已审核不可删
        const canDelete = o.status === 'pending' || o.status === 'void';
        const deleteBtn = canDelete
            ? `<button class="btn-link danger" onclick="deleteInbound(${o.id})">删除</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('${_ibDeleteDisabledTip(o.status)}','error')">删除</button>`;

        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showInboundDetail(${o.id})">${o.orderNo}</a></td>
            <td>${o.refNo || '—'}</td>
            <td>${o.supplierName || '—'}</td>
            <td>${o.warehouseName}</td>
            <td>${o.inboundDate}</td>
            <td>${matCount} 种</td>
            <td>¥${totalAmt.toFixed(2)}</td>
            <td>${o.operatorName}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showInboundDetail(${o.id})">详情</button>
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
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="ibChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="ibChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="ibChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('ibPagination').innerHTML = pgHtml;
}

function _ibAuditDisabledTip(status) {
    if (status === 'approved') return '该入库单已审核';
    if (status === 'void')     return '该入库单已作废，无需审核';
    if (status === 'deleted')  return '已删除入库单不可操作';
    return '当前状态不可审核';
}

function _ibDeleteDisabledTip(status) {
    if (status === 'approved') return '已审核入库单不可删除';
    if (status === 'deleted')  return '该入库单已删除';
    return '当前状态不可删除';
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showInboundDetail(id) {
    const o = (window.mockData.inboundOrders || []).find(x => x.id === id);
    if (!o) return;
    const sm      = IB_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
    const totalAmt = (o.items || []).reduce((s, it) => s + (it.quantity||0)*(it.unitPrice||0), 0);

    const itemsHtml = (o.items || []).map(it => `
        <tr>
            <td>${it.materialName}</td>
            <td>${it.spec}</td>
            <td>${it.unit}</td>
            <td>${it.quantity}</td>
            <td>¥${(it.unitPrice||0).toFixed(2)}</td>
            <td>¥${((it.quantity||0)*(it.unitPrice||0)).toFixed(2)}</td>
            <td>${it.batchNo || '—'}</td>
            <td>${it.expiryDate || '—'}</td>
            <td>${it.remark || '—'}</td>
        </tr>`).join('');

    // 审核信息区块（仅 approved/void 且有 auditAt 时显示）
    const auditSection = o.auditAt ? `
        <div class="detail-section">
            <div class="detail-section-title">审核信息</div>
            <div class="detail-grid">
                <div class="detail-item"><span class="detail-label">审核结果</span>
                    <span class="detail-value">
                        <span class="tag ${sm.cls}">${sm.label}</span>
                    </span>
                </div>
                <div class="detail-item"><span class="detail-label">审核时间</span><span class="detail-value">${o.auditAt}</span></div>
                ${o.auditRemark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">审核意见</span><span class="detail-value">${o.auditRemark}</span></div>` : ''}
            </div>
        </div>` : '';

    const html = `
        <div class="modal-header">
            <span class="modal-title">${o.orderNo} - 入库单详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">入库单号</span><span class="detail-value">${o.orderNo}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">关联单据类型</span><span class="detail-value">${IB_REF_TYPE_MAP[o.refType] || o.refType || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">关联单据号</span><span class="detail-value">${o.refNo || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">供应商</span><span class="detail-value">${o.supplierName || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">入库仓库</span><span class="detail-value">${o.warehouseName}</span></div>
                    <div class="detail-item"><span class="detail-label">入库日期</span><span class="detail-value">${o.inboundDate}</span></div>
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
                            <th>入库数量</th><th>单价</th><th>金额</th>
                            <th>批次号</th><th>保质期至</th><th>备注</th>
                        </tr></thead>
                        <tbody>${itemsHtml || '<tr><td colspan="9" style="text-align:center;color:#909399">暂无物料</td></tr>'}</tbody>
                        <tfoot><tr style="background:#f5f7fa">
                            <td colspan="5" style="text-align:right;font-weight:bold;padding:12px">合计金额</td>
                            <td style="font-weight:bold;color:#f56c6c;padding:12px">¥${totalAmt.toFixed(2)}</td>
                            <td colspan="3"></td>
                        </tr></tfoot>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;
    showModal(html, '880px');
}

/* ============================================================
   审核弹窗
   ============================================================ */
function openIbAuditModal(id) {
    const o = (window.mockData.inboundOrders || []).find(x => x.id === id);
    if (!o) return;
    if (o.status !== 'pending') {
        showToast(_ibAuditDisabledTip(o.status), 'error');
        return;
    }

    const totalAmt = (o.items || []).reduce((s, it) => s + (it.quantity||0)*(it.unitPrice||0), 0);
    const matCount = (o.items || []).length;

    const html = `
        <div class="modal-header">
            <span class="modal-title">审核入库单</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="background:#f5f7fa;border-radius:6px;padding:16px;margin-bottom:20px">
                <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px 20px;font-size:14px">
                    <div><span style="color:#909399">入库单号：</span><span style="color:#303133;font-weight:500">${o.orderNo}</span></div>
                    <div><span style="color:#909399">供应商：</span><span style="color:#303133">${o.supplierName || '—'}</span></div>
                    <div><span style="color:#909399">入库仓库：</span><span style="color:#303133">${o.warehouseName}</span></div>
                    <div><span style="color:#909399">入库日期：</span><span style="color:#303133">${o.inboundDate}</span></div>
                    <div><span style="color:#909399">物料种数：</span><span style="color:#303133">${matCount} 种</span></div>
                    <div><span style="color:#909399">入库金额：</span><span style="color:#f56c6c;font-weight:500">¥${totalAmt.toFixed(2)}</span></div>
                    ${o.remark ? `<div style="grid-column:span 2"><span style="color:#909399">备注：</span><span style="color:#303133">${o.remark}</span></div>` : ''}
                </div>
            </div>
            <div class="form-item">
                <label class="form-label">审核意见</label>
                <textarea class="form-textarea" id="fIbAuditRemark" rows="3"
                          placeholder="请输入审核意见（可选）" style="width:100%"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="rejectInbound(${id})" style="margin-left:8px">审核驳回</button>
            <button class="btn btn-primary" onclick="approveInbound(${id})" style="margin-left:8px">审核通过</button>
        </div>`;
    showModal(html, '580px');
}

function approveInbound(id) {
    const o = (window.mockData.inboundOrders || []).find(x => x.id === id);
    if (!o) return;
    const remark = document.getElementById('fIbAuditRemark')?.value || '';
    o.status      = 'approved';
    o.auditAt     = _ibNow();
    o.auditRemark = remark;
    o.updatedAt   = _ibNow();
    closeModal();
    showToast('审核通过，入库单已审核');
    _filterInbound();
}

function rejectInbound(id) {
    const o = (window.mockData.inboundOrders || []).find(x => x.id === id);
    if (!o) return;
    const remark = document.getElementById('fIbAuditRemark')?.value || '';
    o.status      = 'void';
    o.auditAt     = _ibNow();
    o.auditRemark = remark;
    o.updatedAt   = _ibNow();
    closeModal();
    showToast('审核驳回，入库单已作废', 'error');
    _filterInbound();
}

/* ============================================================
   删除（软删除）
   ============================================================ */
function deleteInbound(id) {
    const o = (window.mockData.inboundOrders || []).find(x => x.id === id);
    if (!o) return;
    if (o.status === 'approved') {
        showToast('已审核入库单不可删除', 'error');
        return;
    }
    if (o.status === 'deleted') {
        showToast('该入库单已删除', 'error');
        return;
    }

    const html = `
        <div class="modal-header">
            <span class="modal-title">删除入库单</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div style="text-align:center;padding:20px 0">
                <div style="font-size:48px;margin-bottom:12px">⚠️</div>
                <div style="font-size:16px;font-weight:500;color:#303133;margin-bottom:8px">
                    确认删除入库单 <span style="color:#f56c6c">${o.orderNo}</span> 吗？
                </div>
                <div style="font-size:13px;color:#909399">
                    删除后数据将标记为已删除，不可恢复
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-danger"  onclick="_doDeleteInbound(${id})" style="margin-left:8px">确认删除</button>
        </div>`;
    showModal(html, '420px');
}

function _doDeleteInbound(id) {
    const o = (window.mockData.inboundOrders || []).find(x => x.id === id);
    if (!o) return;
    o.status    = 'deleted';
    o.updatedAt = _ibNow();
    closeModal();
    showToast('入库单已删除');
    _filterInbound();
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openInboundModal(id) {
    const isEdit = id != null;
    const o = isEdit
        ? JSON.parse(JSON.stringify((window.mockData.inboundOrders || []).find(x => x.id === id) || {}))
        : { id: null, orderNo: _genIbNo(), refType: 'none', refNo: '', supplierId: '', supplierName: '',
            warehouseId: '', warehouseName: '', inboundDate: _ibToday(),
            operatorName: '张三', remark: '', status: 'pending', items: [] };

    if (isEdit && o.status !== 'pending') {
        showToast('仅待审核状态的入库单可编辑', 'error');
        return;
    }

    const supOptions = (window.mockData.suppliers || [])
        .filter(s => s.status === 'active')
        .map(s => `<option value="${s.id}" data-name="${s.supplierName}" ${o.supplierId==s.id?'selected':''}>${s.supplierName}</option>`)
        .join('');

    const whOptions = (window.mockData.warehouses || [])
        .map(w => `<option value="${w.id}" data-name="${w.warehouseName}" ${o.warehouseId==w.id?'selected':''}>${w.warehouseName}</option>`)
        .join('');

    const poOptions = (window.mockData.purchaseOrders || [])
        .filter(p => ['approved','partial'].includes(p.status))
        .map(p => `<option value="${p.orderNo}" ${o.refNo===p.orderNo?'selected':''}>${p.orderNo} - ${p.supplierName}</option>`)
        .join('');

    window._ibRowIdx = 2000;

    const html = `
        <div class="modal-header">
            <span class="modal-title">${isEdit ? '编辑入库单' : '新增入库单'}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px">
                <div class="form-item">
                    <label class="form-label required">入库单号</label>
                    <input class="form-input" id="fIbNo" value="${o.orderNo}" readonly
                           style="background:#f5f7fa;cursor:not-allowed">
                </div>
                <div class="form-item">
                    <label class="form-label">关联单据类型</label>
                    <select class="form-select" id="fIbRefType" onchange="onIbRefTypeChange()">
                        <option value="none"     ${o.refType==='none'    ?'selected':''}>无关联</option>
                        <option value="purchase" ${o.refType==='purchase'?'selected':''}>采购订单</option>
                    </select>
                </div>
                <div class="form-item" id="fIbRefNoWrap"
                     style="${o.refType==='purchase'?'':'display:none'}">
                    <label class="form-label">关联采购订单号</label>
                    <select class="form-select" id="fIbRefNo" onchange="onIbRefNoChange()">
                        <option value="">请选择采购订单</option>
                        ${poOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">供应商</label>
                    <select class="form-select" id="fIbSupplierId">
                        <option value="">请选择供应商</option>
                        ${supOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">入库仓库</label>
                    <select class="form-select" id="fIbWarehouseId">
                        <option value="">请选择仓库</option>
                        ${whOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">入库日期</label>
                    <input type="date" class="form-input" id="fIbDate" value="${o.inboundDate}">
                </div>
                <div class="form-item">
                    <label class="form-label">操作人</label>
                    <input class="form-input" id="fIbOperator" value="${o.operatorName}">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fIbRemark" rows="2">${o.remark || ''}</textarea>
                </div>
            </div>

            <!-- 物料清单 -->
            <div class="dynamic-table" style="margin-top:20px">
                <div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;
                            border-bottom:1px solid #ebeef5;padding-bottom:8px;
                            display:flex;justify-content:space-between;align-items:center">
                    <span>物料清单 <span style="color:#f56c6c;font-size:12px">*</span></span>
                    <span style="font-size:13px;color:#606266;font-weight:normal">
                        合计金额：¥<span id="ibTotalAmt">0.00</span>
                    </span>
                </div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr style="background:#f5f7fa">
                            <th style="min-width:110px">物料名称</th>
                            <th style="min-width:110px">规格</th>
                            <th style="min-width:60px">单位</th>
                            <th style="min-width:75px">入库数量</th>
                            <th style="min-width:80px">单价（元）</th>
                            <th style="min-width:75px">金额</th>
                            <th style="min-width:120px">批次号</th>
                            <th style="min-width:130px">保质期至</th>
                            <th style="min-width:80px">备注</th>
                            <th style="min-width:50px">操作</th>
                        </tr></thead>
                        <tbody id="ibItemBody">
                            ${(o.items || []).map((it, idx) => _buildIbItemRow(idx, it)).join('')}
                        </tbody>
                    </table>
                </div>
                <button class="btn btn-default add-row-btn" onclick="addIbItemRow()" style="margin-top:8px">＋ 添加物料</button>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveInbound(${id || 'null'})">提交入库</button>
        </div>`;

    showModal(html, 'min(980px, 96vw)');
    _calcIbTotal();
    if (!(o.items || []).length) addIbItemRow();
}

function onIbRefTypeChange() {
    const type = document.getElementById('fIbRefType')?.value;
    const wrap = document.getElementById('fIbRefNoWrap');
    if (wrap) wrap.style.display = type === 'purchase' ? '' : 'none';
}

function onIbRefNoChange() {
    const orderNo = document.getElementById('fIbRefNo')?.value;
    if (!orderNo) return;
    const po = (window.mockData.purchaseOrders || []).find(p => p.orderNo === orderNo);
    if (!po) return;
    const supSel = document.getElementById('fIbSupplierId');
    if (supSel) {
        for (let i = 0; i < supSel.options.length; i++) {
            if (supSel.options[i].dataset.name === po.supplierName) {
                supSel.selectedIndex = i;
                break;
            }
        }
    }
}

/* ============================================================
   物料明细行
   ============================================================ */
function _buildIbItemRow(idx, it) {
    it = it || {};
    const materials  = (window.mockData.materials || []).map(m => ({
        id: m.id, name: m.materialName, unit: m.unit, spec: m.spec || ''
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

    return `<tr id="ibRow_${idx}">
        <td>
            <select class="form-select" style="width:110px" onchange="onIbMatChange(this,${idx})">
                <option value="">请选择</option>
                ${matOptions}
            </select>
        </td>
        <td>
            <select class="form-select" style="width:110px" id="ibSpec_${idx}">
                <option value="">请选择规格</option>
                ${specOptions}
            </select>
        </td>
        <td>
            <input class="form-input" style="width:55px;background:#f5f7fa;cursor:not-allowed"
                   id="ibUnit_${idx}" value="${it.unit||''}" readonly>
        </td>
        <td>
            <input type="number" class="form-input" style="width:72px" id="ibQty_${idx}"
                   value="${it.quantity||''}" min="0.01" step="0.01" placeholder="数量"
                   oninput="_calcIbTotal()">
        </td>
        <td>
            <input type="number" class="form-input" style="width:78px" id="ibPrice_${idx}"
                   value="${it.unitPrice||''}" min="0" step="0.01" placeholder="0.00"
                   oninput="_calcIbTotal()">
        </td>
        <td><span id="ibAmt_${idx}" style="font-size:14px;color:#303133">¥0.00</span></td>
        <td>
            <input class="form-input" style="width:115px" id="ibBatch_${idx}"
                   value="${it.batchNo||''}" placeholder="批次号">
        </td>
        <td>
            <input type="date" class="form-input" style="width:125px" id="ibExpiry_${idx}"
                   value="${it.expiryDate||''}">
        </td>
        <td>
            <input class="form-input" style="width:75px" id="ibRowRemark_${idx}"
                   value="${it.remark||''}" placeholder="备注">
        </td>
        <td>
            <button class="btn-link danger" onclick="removeIbItemRow(${idx})">删除</button>
        </td>
    </tr>`;
}

function addIbItemRow() {
    window._ibRowIdx++;
    const tbody = document.getElementById('ibItemBody');
    if (!tbody) return;
    const tr  = document.createElement('tr');
    tr.id     = `ibRow_${window._ibRowIdx}`;
    const tmp = document.createElement('tbody');
    tmp.innerHTML = _buildIbItemRow(window._ibRowIdx, {});
    const built = tmp.querySelector('tr');
    tr.innerHTML  = built ? built.innerHTML : '';
    tbody.appendChild(tr);
    _calcIbTotal();
}

function removeIbItemRow(idx) {
    const row = document.getElementById(`ibRow_${idx}`);
    if (row) row.remove();
    _calcIbTotal();
}

function onIbMatChange(sel, idx) {
    const opt  = sel.options[sel.selectedIndex];
    const unit = opt.dataset.unit || '';
    const spec = opt.dataset.spec || '';

    const unitEl = document.getElementById(`ibUnit_${idx}`);
    if (unitEl) unitEl.value = unit;

    const specEl = document.getElementById(`ibSpec_${idx}`);
    if (specEl) {
        const matName  = opt.dataset.name || '';
        const selMat   = (window.mockData.materials || []).find(m => m.materialName === matName);
        const specList = selMat ? selMat.spec.split(';').map(s=>s.trim()).filter(Boolean) : [];
        const opts = specList.length
            ? specList.map(s => `<option value="${s}">${s}</option>`).join('')
            : (spec ? `<option value="${spec}">${spec}</option>` : '');
        specEl.innerHTML = `<option value="">请选择规格</option>${opts}`;
    }
    _calcIbTotal();
}

function _calcIbTotal() {
    const tbody = document.getElementById('ibItemBody');
    if (!tbody) return;
    let total = 0;
    tbody.querySelectorAll('tr').forEach(tr => {
        const idx   = tr.id.replace('ibRow_', '');
        const qty   = parseFloat(document.getElementById(`ibQty_${idx}`)?.value)   || 0;
        const price = parseFloat(document.getElementById(`ibPrice_${idx}`)?.value) || 0;
        const amt   = qty * price;
        total += amt;
        const amtEl = document.getElementById(`ibAmt_${idx}`);
        if (amtEl) amtEl.textContent = '¥' + amt.toFixed(2);
    });
    const el = document.getElementById('ibTotalAmt');
    if (el) el.textContent = total.toFixed(2);
}

/* ============================================================
   保存
   ============================================================ */
function saveInbound(id) {
    const orderNo     = document.getElementById('fIbNo')?.value || '';
    const whSel       = document.getElementById('fIbWarehouseId');
    const warehouseId = whSel?.value || '';
    const inboundDate = document.getElementById('fIbDate')?.value || '';

    if (!orderNo)     { showToast('入库单号不能为空', 'error'); return; }
    if (!warehouseId) { showToast('请选择入库仓库', 'error');   return; }
    if (!inboundDate) { showToast('请选择入库日期', 'error');   return; }

    const warehouseName = whSel.options[whSel.selectedIndex]?.dataset?.name
                       || whSel.options[whSel.selectedIndex]?.text || '';

    const supSel       = document.getElementById('fIbSupplierId');
    const supplierId   = supSel?.value  || '';
    const supplierName = supSel?.options[supSel.selectedIndex]?.dataset?.name
                      || supSel?.options[supSel.selectedIndex]?.text || '';

    const refType = document.getElementById('fIbRefType')?.value || 'none';
    const refNo   = refType === 'purchase'
        ? (document.getElementById('fIbRefNo')?.value || '') : '';

    const items = [];
    const tbody = document.getElementById('ibItemBody');
    if (tbody) {
        tbody.querySelectorAll('tr').forEach(tr => {
            const idx    = tr.id.replace('ibRow_', '');
            const matSel = tr.querySelector('select');
            const matId  = matSel ? matSel.value : '';
            if (!matId) return;
            const matName  = matSel.options[matSel.selectedIndex]?.dataset?.name
                          || matSel.options[matSel.selectedIndex]?.text || '';
            const specEl   = document.getElementById(`ibSpec_${idx}`);
            const spec     = specEl?.value || '';
            const unit     = document.getElementById(`ibUnit_${idx}`)?.value    || '';
            const qty      = parseFloat(document.getElementById(`ibQty_${idx}`)?.value)   || 0;
            const price    = parseFloat(document.getElementById(`ibPrice_${idx}`)?.value) || 0;
            const batchNo  = document.getElementById(`ibBatch_${idx}`)?.value   || '';
            const expiry   = document.getElementById(`ibExpiry_${idx}`)?.value  || '';
            const remark   = document.getElementById(`ibRowRemark_${idx}`)?.value || '';
            if (!matName || qty <= 0) return;
            items.push({ materialId: Number(matId), materialName: matName, spec, unit,
                         quantity: qty, unitPrice: price, batchNo, expiryDate: expiry, remark });
        });
    }

    if (!items.length) { showToast('请至少添加一条物料清单', 'error'); return; }

    const now = _ibNow();

    if (id) {
        const record = (window.mockData.inboundOrders || []).find(o => o.id === id);
        if (record) {
            record.refType       = refType;
            record.refNo         = refNo;
            record.supplierId    = supplierId ? Number(supplierId) : '';
            record.supplierName  = supplierId ? supplierName : '';
            record.warehouseId   = Number(warehouseId);
            record.warehouseName = warehouseName;
            record.inboundDate   = inboundDate;
            record.operatorName  = document.getElementById('fIbOperator')?.value || '张三';
            record.remark        = document.getElementById('fIbRemark')?.value   || '';
            record.items         = items;
            record.updatedAt     = now;
        }
        closeModal();
        showToast('入库单编辑成功');
    } else {
        const orders = window.mockData.inboundOrders || (window.mockData.inboundOrders = []);
        orders.push({
            id:            orders.length ? Math.max(...orders.map(o => o.id)) + 1 : 1,
            orderNo,
            refType,
            refNo,
            supplierId:    supplierId ? Number(supplierId) : '',
            supplierName:  supplierId ? supplierName : '',
            warehouseId:   Number(warehouseId),
            warehouseName,
            inboundDate,
            operatorId:    1,
            operatorName:  document.getElementById('fIbOperator')?.value || '张三',
            remark:        document.getElementById('fIbRemark')?.value   || '',
            status:        'pending',
            tenantId:      1,
            createdAt:     now,
            updatedAt:     now,
            items
        });
        closeModal();
        showToast('入库单新增成功');
    }

    _filterInbound();
}

/* ============================================================
   工具函数
   ============================================================ */
function _genIbNo() {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(Math.floor(Math.random() * 900 + 100));
    return `IB-${d}-${seq}`;
}

function _ibToday() { return new Date().toISOString().slice(0, 10); }

function _ibNow()   { return new Date().toLocaleString('sv-SE').replace('T', ' '); }
