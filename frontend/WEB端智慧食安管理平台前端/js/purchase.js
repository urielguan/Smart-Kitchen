/**
 * purchase.js — 采购订单管理
 *
 * 功能：
 *  1. 订单列表（分页 + 多条件筛选）
 *  2. 新增 / 编辑订单弹窗（含物料明细行动态增删）
 *  3. 订单详情弹窗
 *  4. 删除 / 取消订单操作
 */

/* ============================================================
   常量
   ============================================================ */
const PO_STATUS_MAP = {
    draft:     { label: '草稿',   cls: 'tag-info'    },
    pending:   { label: '待审核', cls: 'tag-warning'  },
    approved:  { label: '已审核', cls: 'tag-primary'  },
    partial:   { label: '部分到货', cls: 'tag-warning' },
    received:  { label: '已收货', cls: 'tag-success'  },
    cancelled: { label: '已取消', cls: 'tag-danger'   }
};

/* 物料选项（基于 mockData.materials，仅取常用字段） */
function _getPOMaterials() {
    return (window.mockData.materials || []).map(m => ({
        id: m.id,
        name: m.materialName,
        unit: m.unit,
        specs: (m.spec || '').split('/')
    }));
}

/* ============================================================
   状态变量
   ============================================================ */
window.poPage          = 1;
window.poPageSize      = 10;
window.poFilteredList  = [];

/* ============================================================
   渲染入口
   ============================================================ */
function renderPurchasePage(container) {
    container.innerHTML = `
        <!-- 工具栏 -->
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="poSearchNo"   placeholder="订单编号"   style="width:160px">
                <input class="form-input" id="poSearchSup"  placeholder="供应商名称" style="width:160px">
                <select class="form-select" id="poSearchStatus" style="width:130px">
                    <option value="">全部状态</option>
                    <option value="draft">草稿</option>
                    <option value="pending">待审核</option>
                    <option value="approved">已审核</option>
                    <option value="partial">部分到货</option>
                    <option value="received">已收货</option>
                    <option value="cancelled">已取消</option>
                </select>
                <input type="date" class="form-input" id="poSearchDateFrom" style="width:145px" title="订单日期起">
                <input type="date" class="form-input" id="poSearchDateTo"   style="width:145px" title="订单日期止">
                <button class="btn btn-primary" onclick="searchPurchaseOrders()">搜索</button>
                <button class="btn btn-default" onclick="resetPurchaseSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openPurchaseModal()">＋ 新建订单</button>
            </div>
        </div>

        <!-- 表格 -->
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>订单编号</th>
                        <th>供应商</th>
                        <th>订单日期</th>
                        <th>预计到货</th>
                        <th>订单金额</th>
                        <th>状态</th>
                        <th>采购员</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="poTableBody"></tbody>
            </table>
            <div class="pagination" id="poPagination"></div>
        </div>`;

    _filterPurchaseOrders();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterPurchaseOrders() {
    const no       = (document.getElementById('poSearchNo')?.value   || '').trim().toLowerCase();
    const sup      = (document.getElementById('poSearchSup')?.value  || '').trim().toLowerCase();
    const status   = document.getElementById('poSearchStatus')?.value || '';
    const dateFrom = document.getElementById('poSearchDateFrom')?.value || '';
    const dateTo   = document.getElementById('poSearchDateTo')?.value   || '';

    window.poFilteredList = (window.mockData.purchaseOrders || []).filter(o => {
        if (no     && !o.orderNo.toLowerCase().includes(no))           return false;
        if (sup    && !o.supplierName.toLowerCase().includes(sup))     return false;
        if (status && o.status !== status)                              return false;
        if (dateFrom && o.orderDate < dateFrom)                        return false;
        if (dateTo   && o.orderDate > dateTo)                          return false;
        return true;
    });

    window.poPage = 1;
    _renderPurchaseRows();
}

function searchPurchaseOrders()  { _filterPurchaseOrders(); }
function resetPurchaseSearch() {
    ['poSearchNo','poSearchSup','poSearchStatus','poSearchDateFrom','poSearchDateTo']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterPurchaseOrders();
}

function poChangePage(p) { window.poPage = p; _renderPurchaseRows(); }

function _renderPurchaseRows() {
    const list     = window.poFilteredList;
    const total    = list.length;
    const pageSize = window.poPageSize;
    const page     = window.poPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('poTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:#909399">暂无数据</td></tr>`;
        document.getElementById('poPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(o => {
        const sm = PO_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };
        const isCancellable = ['draft','pending','approved'].includes(o.status);
        const isDeletable   = o.status === 'draft';
        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showPurchaseDetail(${o.id})">${o.orderNo}</a></td>
            <td>${o.supplierName}</td>
            <td>${o.orderDate}</td>
            <td>${o.expectedArrival}</td>
            <td>¥${o.totalAmount.toFixed(2)}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>${o.buyerName}</td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showPurchaseDetail(${o.id})">查看</button>
                    ${o.status === 'draft' ? `<button class="btn-link" onclick="openPurchaseModal(${o.id})">编辑</button>` : ''}
                    ${isCancellable ? `<button class="btn-link danger" onclick="cancelPurchaseOrder(${o.id})">取消</button>` : ''}
                    ${isDeletable   ? `<button class="btn-link danger" onclick="deletePurchaseOrder(${o.id})">删除</button>` : ''}
                </div>
            </td>
        </tr>`;
    }).join('');

    // 分页
    const totalPages = Math.ceil(total / pageSize);
    let pgHtml = `<span style="color:#606266;font-size:14px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="poChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="poChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="poChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('poPagination').innerHTML = pgHtml;
}

/* ============================================================
   取消 / 删除
   ============================================================ */
function cancelPurchaseOrder(id) {
    const order = (window.mockData.purchaseOrders || []).find(o => o.id === id);
    if (!order) return;
    if (!confirm(`确定要取消订单「${order.orderNo}」吗？`)) return;
    order.status = 'cancelled';
    order.updatedAt = new Date().toLocaleString('sv-SE').replace('T', ' ');
    showToast('订单已取消');
    _filterPurchaseOrders();
}

function deletePurchaseOrder(id) {
    const idx = (window.mockData.purchaseOrders || []).findIndex(o => o.id === id);
    if (idx < 0) return;
    if (!confirm(`确定要删除该草稿订单吗？`)) return;
    window.mockData.purchaseOrders.splice(idx, 1);
    showToast('删除成功');
    _filterPurchaseOrders();
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showPurchaseDetail(id) {
    const o = (window.mockData.purchaseOrders || []).find(x => x.id === id);
    if (!o) return;
    const sm = PO_STATUS_MAP[o.status] || { label: o.status, cls: 'tag-info' };

    const itemsHtml = (o.items || []).map(it => `
        <tr>
            <td>${it.materialName}</td>
            <td>${it.spec}</td>
            <td>${it.unit}</td>
            <td>${it.quantity}</td>
            <td>¥${it.unitPrice.toFixed(2)}</td>
            <td>¥${it.totalPrice.toFixed(2)}</td>
            <td>${it.receivedQty}</td>
            <td>${it.remark || '—'}</td>
        </tr>`).join('');

    const html = `
        <div class="modal-header">
            <span class="modal-title">采购订单详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">订单编号</span><span class="detail-value">${o.orderNo}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">供应商</span><span class="detail-value">${o.supplierName}</span></div>
                    <div class="detail-item"><span class="detail-label">采购员</span><span class="detail-value">${o.buyerName}</span></div>
                    <div class="detail-item"><span class="detail-label">订单日期</span><span class="detail-value">${o.orderDate}</span></div>
                    <div class="detail-item"><span class="detail-label">预计到货</span><span class="detail-value">${o.expectedArrival}</span></div>
                    <div class="detail-item"><span class="detail-label">归属食堂</span><span class="detail-value">${o.orgName}</span></div>
                    <div class="detail-item"><span class="detail-label">订单金额</span><span class="detail-value" style="color:#f56c6c;font-weight:bold">¥${o.totalAmount.toFixed(2)}</span></div>
                    ${o.remark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${o.remark}</span></div>` : ''}
                    ${o.auditAt ? `<div class="detail-item"><span class="detail-label">审核时间</span><span class="detail-value">${o.auditAt}</span></div>` : ''}
                    ${o.auditRemark ? `<div class="detail-item"><span class="detail-label">审核意见</span><span class="detail-value">${o.auditRemark}</span></div>` : ''}
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">物料明细</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>物料名称</th><th>规格</th><th>单位</th>
                            <th>订购数量</th><th>单价</th><th>金额</th><th>已收数量</th><th>备注</th>
                        </tr></thead>
                        <tbody>${itemsHtml}</tbody>
                        <tfoot><tr style="background:#f5f7fa">
                            <td colspan="5" style="text-align:right;font-weight:bold;padding:12px">合计</td>
                            <td style="font-weight:bold;color:#f56c6c;padding:12px">¥${o.totalAmount.toFixed(2)}</td>
                            <td colspan="2"></td>
                        </tr></tfoot>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>`;
    showModal(html, '860px');
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openPurchaseModal(id) {
    const isEdit = id != null;
    const o = isEdit
        ? JSON.parse(JSON.stringify((window.mockData.purchaseOrders || []).find(x => x.id === id) || {}))
        : { id: null, orderNo: _genPoNo(), supplierId: '', orderDate: _today(), expectedArrival: '', remark: '', status: 'draft', items: [] };

    const supplierOptions = (window.mockData.suppliers || [])
        .filter(s => s.status === 'active')
        .map(s => `<option value="${s.id}" ${o.supplierId == s.id ? 'selected' : ''}>${s.supplierName}</option>`)
        .join('');

    const html = `
        <div class="modal-header">
            <span class="modal-title">${isEdit ? '编辑采购订单' : '新建采购订单'}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px">
                <div class="form-item">
                    <label class="form-label required">订单编号</label>
                    <input class="form-input" id="fPoNo" value="${o.orderNo}" readonly style="background:#f5f7fa;cursor:not-allowed">
                </div>
                <div class="form-item">
                    <label class="form-label required">供应商</label>
                    <select class="form-select" id="fPoSupplierId">
                        <option value="">请选择供应商</option>
                        ${supplierOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">订单日期</label>
                    <input type="date" class="form-input" id="fPoDate" value="${o.orderDate}">
                </div>
                <div class="form-item">
                    <label class="form-label required">预计到货日期</label>
                    <input type="date" class="form-input" id="fPoExpected" value="${o.expectedArrival}">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fPoRemark" rows="2">${o.remark || ''}</textarea>
                </div>
            </div>

            <!-- 物料明细 -->
            <div class="dynamic-table">
                <div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;border-bottom:1px solid #ebeef5;padding-bottom:8px">
                    物料明细
                </div>
                <div style="overflow-x:auto">
                    <table id="poItemTable">
                        <thead><tr style="background:#f5f7fa">
                            <th>物料名称</th><th>规格</th><th>单位</th>
                            <th>数量</th><th>单价（元）</th><th>金额</th><th>备注</th><th>操作</th>
                        </tr></thead>
                        <tbody id="poItemBody">
                            ${(o.items || []).map((it, idx) => _buildPoItemRow(idx, it)).join('')}
                        </tbody>
                    </table>
                </div>
                <button class="btn btn-default add-row-btn" onclick="addPoItemRow()">＋ 添加物料</button>
            </div>

            <div style="text-align:right;font-size:15px;font-weight:bold;color:#f56c6c;margin-top:10px">
                订单合计：¥<span id="poTotalAmount">0.00</span>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="savePurchaseOrder(${id || 'null'})">保存草稿</button>
            ${!isEdit ? `<button class="btn btn-primary" style="background:#67c23a" onclick="savePurchaseOrder(null,'pending')">提交审核</button>` : ''}
        </div>`;
    showModal(html, 'min(900px, 95vw)');

    // 初始化金额
    _calcPoTotal();

    // 若无明细行，自动添加一行
    if (!(o.items || []).length) addPoItemRow();
}

function _buildPoItemRow(idx, it) {
    it = it || {};
    const materials = _getPOMaterials();
    const matOptions = materials.map(m =>
        `<option value="${m.id}" data-unit="${m.unit}" ${it.materialId == m.id ? 'selected' : ''}>${m.name}</option>`
    ).join('');

    return `<tr id="poRow_${idx}">
        <td>
            <select class="form-select" style="width:110px" onchange="onPoMatChange(this,${idx})">
                <option value="">请选择</option>
                ${matOptions}
            </select>
        </td>
        <td><input class="form-input" style="width:100px" id="poSpec_${idx}" value="${it.spec||''}" placeholder="规格"></td>
        <td><input class="form-input" style="width:60px" id="poUnit_${idx}" value="${it.unit||''}" readonly style="background:#f5f7fa"></td>
        <td><input type="number" class="form-input" style="width:70px" id="poQty_${idx}" value="${it.quantity||''}" min="0.01" step="0.01" oninput="_calcPoTotal()"></td>
        <td><input type="number" class="form-input" style="width:80px" id="poPrice_${idx}" value="${it.unitPrice||''}" min="0" step="0.01" placeholder="0.00" oninput="_calcPoTotal()"></td>
        <td><span id="poAmt_${idx}" style="font-size:14px;color:#303133">¥0.00</span></td>
        <td><input class="form-input" style="width:90px" id="poRowRemark_${idx}" value="${it.remark||''}" placeholder="备注"></td>
        <td><button class="btn-link danger" onclick="removePoItemRow(${idx})">删除</button></td>
    </tr>`;
}

let _poRowIdx = 0;
function addPoItemRow() {
    _poRowIdx++;
    const tbody = document.getElementById('poItemBody');
    if (!tbody) return;
    const tr = document.createElement('tr');
    tr.id = `poRow_${_poRowIdx}`;
    tr.innerHTML = _buildPoItemRow(_poRowIdx, {}).replace(`<tr id="poRow_${_poRowIdx}">`, '').replace('</tr>', '');
    tbody.appendChild(tr);
}

function removePoItemRow(idx) {
    const row = document.getElementById(`poRow_${idx}`);
    if (row) row.remove();
    _calcPoTotal();
}

function onPoMatChange(sel, idx) {
    const opt = sel.options[sel.selectedIndex];
    const unit = opt.dataset.unit || '';
    const unitEl = document.getElementById(`poUnit_${idx}`);
    if (unitEl) unitEl.value = unit;
    _calcPoTotal();
}

function _calcPoTotal() {
    const tbody = document.getElementById('poItemBody');
    if (!tbody) return;
    let total = 0;
    tbody.querySelectorAll('tr').forEach(tr => {
        const idx = tr.id.replace('poRow_', '');
        const qty   = parseFloat(document.getElementById(`poQty_${idx}`)?.value)   || 0;
        const price = parseFloat(document.getElementById(`poPrice_${idx}`)?.value) || 0;
        const amt   = qty * price;
        total += amt;
        const amtEl = document.getElementById(`poAmt_${idx}`);
        if (amtEl) amtEl.textContent = '¥' + amt.toFixed(2);
    });
    const totalEl = document.getElementById('poTotalAmount');
    if (totalEl) totalEl.textContent = total.toFixed(2);
}

function _genPoNo() {
    const now = new Date();
    const d = now.toISOString().slice(0,10).replace(/-/g,'');
    const seq = String(Math.floor(Math.random() * 90 + 10)).padStart(2,'0');
    return `PO-${d}${seq}`;
}

function _today() {
    return new Date().toISOString().slice(0,10);
}

/* ============================================================
   保存
   ============================================================ */
function savePurchaseOrder(id, submitStatus) {
    const supplierId = document.getElementById('fPoSupplierId')?.value;
    const orderDate  = document.getElementById('fPoDate')?.value;
    const expected   = document.getElementById('fPoExpected')?.value;

    if (!supplierId) { showToast('请选择供应商', 'error'); return; }
    if (!orderDate)  { showToast('请填写订单日期', 'error'); return; }
    if (!expected)   { showToast('请填写预计到货日期', 'error'); return; }

    const supplier = (window.mockData.suppliers || []).find(s => s.id == supplierId);

    // 收集物料明细
    const items = [];
    const tbody = document.getElementById('poItemBody');
    if (tbody) {
        tbody.querySelectorAll('tr').forEach(tr => {
            const idx = tr.id.replace('poRow_', '');
            const matSel = tr.querySelector('select');
            const matId  = matSel ? matSel.value : '';
            if (!matId) return;
            const matName = matSel.options[matSel.selectedIndex]?.text || '';
            const spec    = document.getElementById(`poSpec_${idx}`)?.value || '';
            const unit    = document.getElementById(`poUnit_${idx}`)?.value || '';
            const qty     = parseFloat(document.getElementById(`poQty_${idx}`)?.value) || 0;
            const price   = parseFloat(document.getElementById(`poPrice_${idx}`)?.value) || 0;
            const remark  = document.getElementById(`poRowRemark_${idx}`)?.value || '';
            if (qty <= 0) return;
            items.push({ id: Date.now() + items.length, materialId: Number(matId), materialName: matName, spec, unit, quantity: qty, unitPrice: price, totalPrice: qty * price, receivedQty: 0, remark });
        });
    }

    if (!items.length) { showToast('请至少添加一条物料明细', 'error'); return; }

    const total = items.reduce((s, it) => s + it.totalPrice, 0);
    const now   = new Date().toLocaleString('sv-SE').replace('T', ' ');
    const status = submitStatus || 'draft';

    if (id) {
        // 编辑
        const order = (window.mockData.purchaseOrders || []).find(o => o.id === id);
        if (order) {
            order.supplierId      = Number(supplierId);
            order.supplierName    = supplier?.supplierName || '';
            order.orderDate       = orderDate;
            order.expectedArrival = expected;
            order.remark          = document.getElementById('fPoRemark')?.value || '';
            order.items           = items;
            order.totalAmount     = total;
            order.updatedAt       = now;
        }
    } else {
        // 新增
        const newOrder = {
            id:             (window.mockData.purchaseOrders.length
                             ? Math.max(...window.mockData.purchaseOrders.map(o => o.id)) + 1 : 1),
            orderNo:        document.getElementById('fPoNo')?.value || _genPoNo(),
            supplierId:     Number(supplierId),
            supplierName:   supplier?.supplierName || '',
            orderDate,
            expectedArrival: expected,
            totalAmount:    total,
            status,
            buyerId:        1,
            buyerName:      '张三',
            orgId:          3,
            orgName:        '第一食堂',
            remark:         document.getElementById('fPoRemark')?.value || '',
            auditAt:        null,
            auditRemark:    null,
            tenantId:       1,
            createdAt:      now,
            updatedAt:      now,
            items
        };
        window.mockData.purchaseOrders.push(newOrder);
    }

    closeModal();
    showToast(status === 'pending' ? '订单已提交审核' : '保存成功');
    _filterPurchaseOrders();
}
