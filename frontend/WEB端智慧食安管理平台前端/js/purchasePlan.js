/**
 * purchasePlan.js — 采购计划管理
 *
 * ===== 运行验证说明 =====
 *  1. 双击 index.html → 点击左侧「采购管理 / 采购计划」导航进入版块
 *
 *  【优化点1：审核按钮及状态流转】
 *  - 找到状态为「已提交」的行（蓝色标签），点击「审核」按钮
 *  - 弹出审核弹窗，可填写审核意见
 *  - 点击「审核通过」→ 状态变为绿色「已审核」，顶部提示"采购计划审核通过"
 *  - 点击「审核驳回」→ 状态变为红色「已驳回」，顶部提示"采购计划审核驳回"
 *  - 点击草稿/已审核/已驳回行的「审核」（灰色置灰）→ 提示"仅已提交状态可审核"
 *
 *  【优化点2：已审核计划生成采购订单】
 *  - 找到状态为「已审核」的行（绿色标签），点击「生成采购订单」按钮
 *  - 弹出确认弹窗，点击「确认生成」
 *  - 提示"采购订单生成成功，可前往采购订单管理版块查看"，并附跳转按钮
 *  - 切换至「采购订单」版块，可在列表末尾找到刚生成的新订单（状态：待下单）
 *
 * ===== 核心改动点 =====
 *  1. PP_STATUS_MAP 新增 approved（已审核/绿）、rejected（已驳回/红）两个状态
 *  2. 搜索栏状态下拉同步新增「已审核」「已驳回」选项
 *  3. _renderPurchasePlanRows：「执行」按钮 → 「审核」按钮（仅已提交可点，其余置灰）
 *  4. 新增 openAuditModal(id)：审核弹窗（审核意见输入 + 通过/驳回操作）
 *  5. 新增 approvePurchasePlan(id) / rejectPurchasePlan(id)：审核通过/驳回逻辑
 *  6. _renderPurchasePlanRows：「已审核」行新增「生成采购订单」按钮
 *  7. 新增 genPurchaseOrderFromPlan(id)：确认弹窗 + 数据联动生成 purchaseOrders 条目
 *  8. 删除原 executePurchasePlan(id) 函数（已替换）
 */

/* ============================================================
   常量
   ============================================================ */
const PP_STATUS_MAP = {
    draft:    { label: '草稿',   cls: 'tag-info'    },
    submitted:{ label: '已提交', cls: 'tag-primary'  },
    approved: { label: '已审核', cls: 'tag-success'  },
    rejected: { label: '已驳回', cls: 'tag-danger'   }
};

const PP_PERIOD_MAP = {
    day:   '日计划',
    week:  '周计划',
    month: '月计划'
};

function _getPPMaterials() {
    return (window.mockData.materials || []).map(m => ({
        id:   m.id,
        name: m.materialName,
        unit: m.unit,
        spec: m.spec || ''
    }));
}

function _getPPSpecOptions(materialName) {
    const mat = (window.mockData.materials || []).find(m => m.materialName === materialName);
    if (!mat || !mat.spec) return [];
    return mat.spec.split(';').map(s => s.trim()).filter(Boolean);
}

/* ============================================================
   状态变量
   ============================================================ */
window.ppPage         = 1;
window.ppPageSize     = 10;
window.ppFilteredList = [];
window._ppRowIdx      = 0;

/* ============================================================
   渲染入口
   ============================================================ */
function renderPurchasePlanPage(container) {
    container.innerHTML = `
        <div class="toolbar">
            <div class="toolbar-row">
                <input class="form-input" id="ppSearchKey"    placeholder="计划编号/名称" style="width:180px">
                <input class="form-input" id="ppSearchOrg"    placeholder="所属组织"     style="width:150px">
                <select class="form-select" id="ppSearchStatus" style="width:120px">
                    <option value="">全部状态</option>
                    <option value="draft">草稿</option>
                    <option value="submitted">已提交</option>
                    <option value="approved">已审核</option>
                    <option value="rejected">已驳回</option>
                </select>
                <button class="btn btn-primary" onclick="searchPurchasePlans()">搜索</button>
                <button class="btn btn-default" onclick="resetPurchasePlanSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openPurchasePlanModal()">＋ 新增采购计划</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>计划编号</th>
                        <th>计划名称</th>
                        <th>所属组织</th>
                        <th>计划周期</th>
                        <th>计划日期</th>
                        <th>状态</th>
                        <th>创建人</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="ppTableBody"></tbody>
            </table>
            <div class="pagination" id="ppPagination"></div>
        </div>`;

    _filterPurchasePlans();
}

/* ============================================================
   筛选 & 分页
   ============================================================ */
function _filterPurchasePlans() {
    const key    = (document.getElementById('ppSearchKey')?.value    || '').trim().toLowerCase();
    const org    = (document.getElementById('ppSearchOrg')?.value    || '').trim().toLowerCase();
    const status = (document.getElementById('ppSearchStatus')?.value || '');

    window.ppFilteredList = (window.mockData.purchasePlans || []).filter(p => {
        if (key    && !p.planNo.toLowerCase().includes(key) && !p.planName.toLowerCase().includes(key)) return false;
        if (org    && !p.orgName.toLowerCase().includes(org))  return false;
        if (status && p.status !== status)                      return false;
        return true;
    });

    window.ppPage = 1;
    _renderPurchasePlanRows();
}

function searchPurchasePlans()     { _filterPurchasePlans(); }
function resetPurchasePlanSearch() {
    ['ppSearchKey','ppSearchOrg','ppSearchStatus']
        .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
    _filterPurchasePlans();
}

function ppChangePage(p) { window.ppPage = p; _renderPurchasePlanRows(); }

function _renderPurchasePlanRows() {
    const list     = window.ppFilteredList;
    const total    = list.length;
    const pageSize = window.ppPageSize;
    const page     = window.ppPage;
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const tbody = document.getElementById('ppTableBody');
    if (!tbody) return;

    if (!pageData.length) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:40px;color:#909399">暂无采购计划数据</td></tr>`;
        document.getElementById('ppPagination').innerHTML = '';
        return;
    }

    tbody.innerHTML = pageData.map(p => {
        const sm        = PP_STATUS_MAP[p.status] || { label: p.status, cls: 'tag-info' };
        const periodTxt = PP_PERIOD_MAP[p.period]  || p.period;

        /* 编辑按钮：仅草稿可用 */
        const editBtn = p.status === 'draft'
            ? `<button class="btn-link" onclick="openPurchasePlanModal(${p.id})">编辑</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('仅草稿状态可编辑','error')">编辑</button>`;

        /* 提交按钮：仅草稿显示 */
        const submitBtn = p.status === 'draft'
            ? `<button class="btn-link" onclick="submitPurchasePlan(${p.id})">提交</button>`
            : '';

        /* 审核按钮：已提交可点，其余置灰 */
        const auditBtn = p.status === 'submitted'
            ? `<button class="btn-link" style="color:#409eff" onclick="openAuditModal(${p.id})">审核</button>`
            : `<button class="btn-link" style="color:#c0c4cc;cursor:not-allowed"
                       onclick="showToast('仅已提交状态可审核','error')">审核</button>`;

        /* 生成采购订单按钮：仅已审核显示 */
        const genOrderBtn = p.status === 'approved'
            ? `<button class="btn-link" style="color:#67c23a" onclick="genPurchaseOrderFromPlan(${p.id})">生成采购订单</button>`
            : '';

        return `<tr>
            <td><a href="javascript:void(0)" class="btn-link" onclick="showPurchasePlanDetail(${p.id})">${p.planNo}</a></td>
            <td>${p.planName}</td>
            <td>${p.orgName}</td>
            <td>${periodTxt}</td>
            <td>${p.planDate}</td>
            <td><span class="tag ${sm.cls}">${sm.label}</span></td>
            <td>${p.creatorName}</td>
            <td>${p.createdAt}</td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showPurchasePlanDetail(${p.id})">详情</button>
                    ${editBtn}
                    ${submitBtn}
                    ${auditBtn}
                    ${genOrderBtn}
                </div>
            </td>
        </tr>`;
    }).join('');

    const totalPages = Math.ceil(total / pageSize);
    let pgHtml = `<span style="color:#606266;font-size:14px">共 ${total} 条</span>`;
    if (totalPages > 1) {
        pgHtml += ` <button class="btn btn-default" ${page<=1?'disabled':''} onclick="ppChangePage(${page-1})">上一页</button>`;
        for (let i = 1; i <= totalPages; i++) {
            pgHtml += `<button class="btn ${i===page?'btn-primary':'btn-default'}" onclick="ppChangePage(${i})">${i}</button>`;
        }
        pgHtml += `<button class="btn btn-default" ${page>=totalPages?'disabled':''} onclick="ppChangePage(${page+1})">下一页</button>`;
    }
    document.getElementById('ppPagination').innerHTML = pgHtml;
}

/* ============================================================
   状态操作：提交
   ============================================================ */
function submitPurchasePlan(id) {
    const plan = (window.mockData.purchasePlans || []).find(p => p.id === id);
    if (!plan) return;
    plan.status    = 'submitted';
    plan.updatedAt = _ppNow();
    showToast('采购计划已提交');
    _filterPurchasePlans();
}

/* ============================================================
   【优化点1】审核弹窗
   ============================================================ */
function openAuditModal(id) {
    const plan = (window.mockData.purchasePlans || []).find(p => p.id === id);
    if (!plan) return;
    if (plan.status !== 'submitted') {
        showToast('仅已提交状态可审核', 'error');
        return;
    }

    const html = `
        <div class="modal-header">
            <span class="modal-title">采购计划审核</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div style="background:#f5f7fa;border-radius:6px;padding:14px 16px;margin-bottom:20px">
                <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px 20px">
                    <div>
                        <span style="font-size:12px;color:#909399">计划编号</span>
                        <div style="font-size:14px;color:#303133;margin-top:2px">${plan.planNo}</div>
                    </div>
                    <div>
                        <span style="font-size:12px;color:#909399">计划名称</span>
                        <div style="font-size:14px;color:#303133;margin-top:2px">${plan.planName}</div>
                    </div>
                    <div>
                        <span style="font-size:12px;color:#909399">所属组织</span>
                        <div style="font-size:14px;color:#303133;margin-top:2px">${plan.orgName}</div>
                    </div>
                    <div>
                        <span style="font-size:12px;color:#909399">创建人</span>
                        <div style="font-size:14px;color:#303133;margin-top:2px">${plan.creatorName}</div>
                    </div>
                </div>
            </div>
            <div class="form-item">
                <label class="form-label">审核意见</label>
                <textarea class="form-textarea" id="auditRemark" rows="4"
                          placeholder="请填写审核意见（选填）"
                          style="resize:vertical;min-height:90px"></textarea>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-default"
                    style="color:#f56c6c;border-color:#f56c6c"
                    onclick="rejectPurchasePlan(${id})">审核驳回</button>
            <button class="btn btn-primary"
                    onclick="approvePurchasePlan(${id})">审核通过</button>
        </div>`;

    showModal(html, '520px');
}

function approvePurchasePlan(id) {
    const plan = (window.mockData.purchasePlans || []).find(p => p.id === id);
    if (!plan) return;
    plan.status      = 'approved';
    plan.auditRemark = (document.getElementById('auditRemark')?.value || '').trim() || '审核通过';
    plan.auditAt     = _ppNow();
    plan.updatedAt   = _ppNow();
    closeModal();
    showToast('采购计划审核通过');
    _filterPurchasePlans();
}

function rejectPurchasePlan(id) {
    const plan = (window.mockData.purchasePlans || []).find(p => p.id === id);
    if (!plan) return;
    plan.status      = 'rejected';
    plan.auditRemark = (document.getElementById('auditRemark')?.value || '').trim() || '审核驳回';
    plan.auditAt     = _ppNow();
    plan.updatedAt   = _ppNow();
    closeModal();
    showToast('采购计划审核驳回', 'error');
    _filterPurchasePlans();
}

/* ============================================================
   【优化点2】生成采购订单
   ============================================================ */
function genPurchaseOrderFromPlan(id) {
    const plan = (window.mockData.purchasePlans || []).find(p => p.id === id);
    if (!plan) return;
    if (plan.status !== 'approved') {
        showToast('仅已审核状态的计划可生成采购订单', 'error');
        return;
    }

    /* 检查是否已生成过 */
    const alreadyGen = (window.mockData.purchaseOrders || []).some(o => o.sourcePlanId === id);
    if (alreadyGen) {
        showToast('该计划已生成过采购订单，请勿重复操作', 'error');
        return;
    }

    /* 预览物料清单 */
    const itemsPreview = (plan.items || []).map(it =>
        `<tr>
            <td style="padding:6px 10px">${it.materialName}</td>
            <td style="padding:6px 10px">${it.spec}</td>
            <td style="padding:6px 10px">${it.unit}</td>
            <td style="padding:6px 10px">${it.quantity}</td>
            <td style="padding:6px 10px">¥${(it.unitPrice||0).toFixed(2)}</td>
         </tr>`
    ).join('');

    const totalAmt = (plan.items || []).reduce((s,it) => s + (it.quantity||0)*(it.unitPrice||0), 0);

    const html = `
        <div class="modal-header">
            <span class="modal-title">生成采购订单确认</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div style="display:flex;align-items:flex-start;gap:10px;padding:14px 16px;
                        background:#ecf5ff;border:1px solid #b3d8ff;border-radius:6px;margin-bottom:18px">
                <span style="font-size:20px;line-height:1.4">📋</span>
                <div>
                    <div style="font-size:14px;color:#303133;font-weight:600;margin-bottom:4px">
                        是否确认基于该采购计划生成采购订单？
                    </div>
                    <div style="font-size:13px;color:#606266">
                        将基于计划「${plan.planName}」的物料清单自动生成一条采购订单（状态：待下单），
                        可前往「采购订单」版块填写供应商等信息后提交。
                    </div>
                </div>
            </div>

            <div style="font-size:13px;font-weight:600;color:#303133;margin-bottom:8px">物料清单预览</div>
            <div style="overflow-x:auto;border:1px solid #ebeef5;border-radius:4px">
                <table style="width:100%">
                    <thead style="background:#f5f7fa">
                        <tr>
                            <th style="padding:8px 10px;font-size:13px;text-align:left">物料名称</th>
                            <th style="padding:8px 10px;font-size:13px;text-align:left">规格</th>
                            <th style="padding:8px 10px;font-size:13px;text-align:left">单位</th>
                            <th style="padding:8px 10px;font-size:13px;text-align:left">数量</th>
                            <th style="padding:8px 10px;font-size:13px;text-align:left">预计单价</th>
                        </tr>
                    </thead>
                    <tbody style="font-size:13px;color:#606266">
                        ${itemsPreview}
                    </tbody>
                    <tfoot>
                        <tr style="background:#f5f7fa">
                            <td colspan="4" style="padding:8px 10px;text-align:right;font-weight:bold;font-size:13px">预计合计</td>
                            <td style="padding:8px 10px;color:#f56c6c;font-weight:bold;font-size:13px">¥${totalAmt.toFixed(2)}</td>
                        </tr>
                    </tfoot>
                </table>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" style="background:#67c23a" onclick="_doGenPurchaseOrder(${id})">确认生成</button>
        </div>`;

    showModal(html, '600px');
}

function _doGenPurchaseOrder(planId) {
    const plan = (window.mockData.purchasePlans || []).find(p => p.id === planId);
    if (!plan) return;

    const now     = _ppNow();
    const orderNo = _genOrderNoFromPlan();
    const orders  = window.mockData.purchaseOrders || (window.mockData.purchaseOrders = []);
    const newId   = orders.length ? Math.max(...orders.map(o => o.id)) + 1 : 1;

    /* 将采购计划物料清单映射为订单明细行 */
    const items = (plan.items || []).map((it, idx) => ({
        id:           newId * 100 + idx,
        orderId:      newId,
        materialId:   it.materialId,
        materialName: it.materialName,
        spec:         it.spec,
        unit:         it.unit,
        quantity:     it.quantity,
        unitPrice:    it.unitPrice || 0,
        totalPrice:   (it.quantity || 0) * (it.unitPrice || 0),
        receivedQty:  0,
        remark:       it.remark || ''
    }));

    const totalAmount = items.reduce((s, it) => s + it.totalPrice, 0);

    orders.push({
        id:              newId,
        orderNo,
        supplierId:      '',
        supplierName:    '（待选择供应商）',
        orderDate:       now.slice(0, 10),
        expectedArrival: '',
        totalAmount,
        status:          'draft',         /* 待下单，沿用 draft 便于在采购订单列表直接编辑 */
        buyerId:         plan.creatorId   || 1,
        buyerName:       plan.creatorName || '张三',
        orgId:           plan.orgId,
        orgName:         plan.orgName,
        remark:          `由采购计划「${plan.planName}」自动生成`,
        auditAt:         null,
        auditRemark:     null,
        sourcePlanId:    planId,          /* 标记来源计划，防重复生成 */
        tenantId:        1,
        createdAt:       now,
        updatedAt:       now,
        items
    });

    /* 关闭弹窗后弹出带跳转的成功提示 */
    closeModal();
    _showGenSuccessToast(newId);

    /* 刷新采购计划列表（按钮状态不变，已通过 alreadyGen 检查阻止重复） */
    _filterPurchasePlans();
}

/** 生成订单编号：PO-fromPlan-YYYYMMDD-xxx */
function _genOrderNoFromPlan() {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(Math.floor(Math.random() * 900 + 100));
    return `PO-${d}-${seq}`;
}

/** 带「前往查看」跳转链接的成功 Toast */
function _showGenSuccessToast(orderId) {
    const toast = document.getElementById('toast');
    if (!toast) { showToast('采购订单生成成功'); return; }

    toast.style.background  = '#67c23a';
    toast.style.maxWidth    = '420px';
    toast.innerHTML = `
        采购订单生成成功，可前往采购订单管理版块查看
        <a href="javascript:void(0)"
           onclick="switchPage('purchase','采购管理','采购订单')"
           style="color:#fff;text-decoration:underline;margin-left:8px;font-weight:600">
            前往查看 →
        </a>`;
    toast.classList.add('show');
    setTimeout(() => {
        toast.classList.remove('show');
        toast.style.maxWidth = '';
        toast.style.background = '';
    }, 4000);
}

/* ============================================================
   详情弹窗（新增审核意见展示）
   ============================================================ */
function showPurchasePlanDetail(id) {
    const p = (window.mockData.purchasePlans || []).find(x => x.id === id);
    if (!p) return;
    const sm        = PP_STATUS_MAP[p.status] || { label: p.status, cls: 'tag-info' };
    const periodTxt = PP_PERIOD_MAP[p.period]  || p.period;
    const totalAmt  = (p.items || []).reduce((s, it) => s + (it.quantity * it.unitPrice), 0);

    const itemsHtml = (p.items || []).map(it => `
        <tr>
            <td>${it.materialName}</td>
            <td>${it.spec}</td>
            <td>${it.unit}</td>
            <td>${it.quantity}</td>
            <td>¥${(it.unitPrice || 0).toFixed(2)}</td>
            <td>¥${((it.quantity || 0) * (it.unitPrice || 0)).toFixed(2)}</td>
            <td>${it.remark || '—'}</td>
        </tr>`).join('');

    /* 审核信息（已审核/已驳回才展示） */
    const auditBlock = (p.auditAt)
        ? `<div class="detail-section">
               <div class="detail-section-title">审核信息</div>
               <div class="detail-grid">
                   <div class="detail-item">
                       <span class="detail-label">审核结果</span>
                       <span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span>
                   </div>
                   <div class="detail-item">
                       <span class="detail-label">审核时间</span>
                       <span class="detail-value">${p.auditAt}</span>
                   </div>
                   ${p.auditRemark ? `
                   <div class="detail-item" style="grid-column:span 2">
                       <span class="detail-label">审核意见</span>
                       <span class="detail-value">${p.auditRemark}</span>
                   </div>` : ''}
               </div>
           </div>`
        : '';

    const html = `
        <div class="modal-header">
            <span class="modal-title">采购计划详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">计划编号</span><span class="detail-value">${p.planNo}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value"><span class="tag ${sm.cls}">${sm.label}</span></span></div>
                    <div class="detail-item"><span class="detail-label">计划名称</span><span class="detail-value">${p.planName}</span></div>
                    <div class="detail-item"><span class="detail-label">所属组织</span><span class="detail-value">${p.orgName}</span></div>
                    <div class="detail-item"><span class="detail-label">计划周期</span><span class="detail-value">${periodTxt}</span></div>
                    <div class="detail-item"><span class="detail-label">计划日期</span><span class="detail-value">${p.planDate}</span></div>
                    <div class="detail-item"><span class="detail-label">创建人</span><span class="detail-value">${p.creatorName}</span></div>
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${p.createdAt}</span></div>
                    ${p.remark ? `<div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${p.remark}</span></div>` : ''}
                </div>
            </div>
            ${auditBlock}
            <div class="detail-section">
                <div class="detail-section-title">物料清单</div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr>
                            <th>物料名称</th><th>规格</th><th>单位</th>
                            <th>需求数量</th><th>预计单价</th><th>预计金额</th><th>备注</th>
                        </tr></thead>
                        <tbody>${itemsHtml || '<tr><td colspan="7" style="text-align:center;color:#909399">暂无物料</td></tr>'}</tbody>
                        <tfoot><tr style="background:#f5f7fa">
                            <td colspan="5" style="text-align:right;font-weight:bold;padding:12px">预计合计</td>
                            <td style="font-weight:bold;color:#f56c6c;padding:12px">¥${totalAmt.toFixed(2)}</td>
                            <td></td>
                        </tr></tfoot>
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
   新增 / 编辑弹窗（不变）
   ============================================================ */
function openPurchasePlanModal(id) {
    const isEdit = id != null;
    const p = isEdit
        ? JSON.parse(JSON.stringify((window.mockData.purchasePlans || []).find(x => x.id === id) || {}))
        : { id: null, planNo: _genPPNo(), planName: '', orgId: '', orgName: '', period: 'week', planDate: _ppToday(), remark: '', status: 'draft', items: [] };

    if (isEdit && p.status !== 'draft') {
        showToast('仅草稿状态可编辑', 'error');
        return;
    }

    const orgOptions = (window.mockData.orgs || [])
        .map(o => `<option value="${o.id}" data-name="${o.orgName}" ${p.orgId == o.id ? 'selected' : ''}>${o.orgName}</option>`)
        .join('');

    window._ppRowIdx = 1000;

    const html = `
        <div class="modal-header">
            <span class="modal-title">${isEdit ? '编辑采购计划' : '新增采购计划'}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px">
                <div class="form-item">
                    <label class="form-label">计划编号</label>
                    <input class="form-input" id="fPpNo" value="${p.planNo}" readonly style="background:#f5f7fa;cursor:not-allowed">
                </div>
                <div class="form-item">
                    <label class="form-label required">计划名称</label>
                    <input class="form-input" id="fPpName" value="${p.planName}" placeholder="请输入计划名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">所属组织</label>
                    <select class="form-select" id="fPpOrgId">
                        <option value="">请选择组织</option>
                        ${orgOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">计划周期</label>
                    <select class="form-select" id="fPpPeriod">
                        <option value="day"   ${p.period==='day'   ?'selected':''}>日计划</option>
                        <option value="week"  ${p.period==='week'  ?'selected':''}>周计划</option>
                        <option value="month" ${p.period==='month' ?'selected':''}>月计划</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">计划日期</label>
                    <input type="date" class="form-input" id="fPpDate" value="${p.planDate}">
                </div>
                <div class="form-item">
                    <label class="form-label">备注</label>
                    <input class="form-input" id="fPpRemark" value="${p.remark || ''}" placeholder="选填">
                </div>
            </div>
            <div class="dynamic-table" style="margin-top:20px">
                <div style="font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;
                            border-bottom:1px solid #ebeef5;padding-bottom:8px;
                            display:flex;justify-content:space-between;align-items:center">
                    <span>物料清单 <span style="color:#f56c6c;font-size:12px">*</span></span>
                    <span style="font-size:13px;color:#606266;font-weight:normal">
                        预计合计：¥<span id="ppTotalAmt">0.00</span>
                    </span>
                </div>
                <div style="overflow-x:auto">
                    <table>
                        <thead><tr style="background:#f5f7fa">
                            <th style="min-width:120px">物料名称</th>
                            <th style="min-width:110px">规格</th>
                            <th style="min-width:70px">单位</th>
                            <th style="min-width:80px">需求数量</th>
                            <th style="min-width:90px">预计单价（元）</th>
                            <th style="min-width:80px">预计金额</th>
                            <th style="min-width:90px">备注</th>
                            <th style="min-width:50px">操作</th>
                        </tr></thead>
                        <tbody id="ppItemBody">
                            ${(p.items || []).map((it, idx) => _buildPPItemRow(idx, it)).join('')}
                        </tbody>
                    </table>
                </div>
                <button class="btn btn-default add-row-btn" onclick="addPPItemRow()" style="margin-top:8px">＋ 添加物料</button>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="savePurchasePlan(${id || 'null'}, 'draft')">保存草稿</button>
            ${!isEdit ? `<button class="btn btn-primary" style="background:#67c23a" onclick="savePurchasePlan(null,'submitted')">提交</button>` : ''}
        </div>`;

    showModal(html, 'min(920px, 95vw)');
    _calcPPTotal();
    if (!(p.items || []).length) addPPItemRow();
}

/* ============================================================
   物料明细行
   ============================================================ */
function _buildPPItemRow(idx, it) {
    it = it || {};
    const materials  = _getPPMaterials();
    const matOptions = materials.map(m =>
        `<option value="${m.id}" data-unit="${m.unit}" data-name="${m.name}" data-spec="${m.spec}"
                 ${it.materialId == m.id ? 'selected' : ''}>${m.name}</option>`
    ).join('');

    const specList    = it.materialName ? _getPPSpecOptions(it.materialName) : [];
    const specOptions = specList.length
        ? specList.map(s => `<option value="${s}" ${it.spec===s?'selected':''}>${s}</option>`).join('')
        : (it.spec ? `<option value="${it.spec}" selected>${it.spec}</option>` : '');

    return `<tr id="ppRow_${idx}">
        <td>
            <select class="form-select" style="width:120px" onchange="onPPMatChange(this,${idx})">
                <option value="">请选择</option>
                ${matOptions}
            </select>
        </td>
        <td>
            <select class="form-select" style="width:110px" id="ppSpec_${idx}">
                <option value="">请选择规格</option>
                ${specOptions}
            </select>
        </td>
        <td>
            <input class="form-input" style="width:60px;background:#f5f7fa;cursor:not-allowed"
                   id="ppUnit_${idx}" value="${it.unit||''}" readonly>
        </td>
        <td>
            <input type="number" class="form-input" style="width:75px" id="ppQty_${idx}"
                   value="${it.quantity||''}" min="0.01" step="0.01" placeholder="数量"
                   oninput="_calcPPTotal()">
        </td>
        <td>
            <input type="number" class="form-input" style="width:85px" id="ppPrice_${idx}"
                   value="${it.unitPrice||''}" min="0" step="0.01" placeholder="0.00"
                   oninput="_calcPPTotal()">
        </td>
        <td><span id="ppAmt_${idx}" style="font-size:14px;color:#303133">¥0.00</span></td>
        <td>
            <input class="form-input" style="width:85px" id="ppRowRemark_${idx}"
                   value="${it.remark||''}" placeholder="备注">
        </td>
        <td>
            <button class="btn-link danger" onclick="removePPItemRow(${idx})">删除</button>
        </td>
    </tr>`;
}

function addPPItemRow() {
    window._ppRowIdx++;
    const tbody = document.getElementById('ppItemBody');
    if (!tbody) return;
    const tr  = document.createElement('tr');
    tr.id     = `ppRow_${window._ppRowIdx}`;
    const tmp = document.createElement('tbody');
    tmp.innerHTML = _buildPPItemRow(window._ppRowIdx, {});
    const built = tmp.querySelector('tr');
    tr.innerHTML  = built ? built.innerHTML : '';
    tbody.appendChild(tr);
    _calcPPTotal();
}

function removePPItemRow(idx) {
    const row = document.getElementById(`ppRow_${idx}`);
    if (row) row.remove();
    _calcPPTotal();
}

function onPPMatChange(sel, idx) {
    const opt  = sel.options[sel.selectedIndex];
    const unit = opt.dataset.unit || '';
    const spec = opt.dataset.spec || '';

    const unitEl = document.getElementById(`ppUnit_${idx}`);
    if (unitEl) unitEl.value = unit;

    const specEl = document.getElementById(`ppSpec_${idx}`);
    if (specEl) {
        const matName  = opt.dataset.name || '';
        const specList = _getPPSpecOptions(matName);
        const opts = specList.length
            ? specList.map(s => `<option value="${s}">${s}</option>`).join('')
            : (spec ? `<option value="${spec}">${spec}</option>` : '');
        specEl.innerHTML = `<option value="">请选择规格</option>${opts}`;
    }
    _calcPPTotal();
}

function _calcPPTotal() {
    const tbody = document.getElementById('ppItemBody');
    if (!tbody) return;
    let total = 0;
    tbody.querySelectorAll('tr').forEach(tr => {
        const idx   = tr.id.replace('ppRow_', '');
        const qty   = parseFloat(document.getElementById(`ppQty_${idx}`)?.value)   || 0;
        const price = parseFloat(document.getElementById(`ppPrice_${idx}`)?.value) || 0;
        const amt   = qty * price;
        total += amt;
        const amtEl = document.getElementById(`ppAmt_${idx}`);
        if (amtEl) amtEl.textContent = '¥' + amt.toFixed(2);
    });
    const el = document.getElementById('ppTotalAmt');
    if (el) el.textContent = total.toFixed(2);
}

/* ============================================================
   保存（草稿 / 提交）
   ============================================================ */
function savePurchasePlan(id, saveStatus) {
    const planName = (document.getElementById('fPpName')?.value || '').trim();
    const orgSel   = document.getElementById('fPpOrgId');
    const orgId    = orgSel?.value || '';
    const planDate = document.getElementById('fPpDate')?.value || '';

    if (!planName) { showToast('请填写计划名称', 'error'); return; }
    if (!orgId)    { showToast('请选择所属组织', 'error'); return; }
    if (!planDate) { showToast('请选择计划日期', 'error'); return; }

    const orgName = orgSel.options[orgSel.selectedIndex]?.dataset?.name
                 || orgSel.options[orgSel.selectedIndex]?.text || '';

    const items = [];
    const tbody = document.getElementById('ppItemBody');
    if (tbody) {
        tbody.querySelectorAll('tr').forEach(tr => {
            const idx    = tr.id.replace('ppRow_', '');
            const matSel = tr.querySelector('select');
            const matId  = matSel ? matSel.value : '';
            if (!matId) return;
            const matName = matSel.options[matSel.selectedIndex]?.dataset?.name
                         || matSel.options[matSel.selectedIndex]?.text || '';
            const specEl  = document.getElementById(`ppSpec_${idx}`);
            const spec    = specEl?.value || '';
            const unit    = document.getElementById(`ppUnit_${idx}`)?.value   || '';
            const qty     = parseFloat(document.getElementById(`ppQty_${idx}`)?.value)   || 0;
            const price   = parseFloat(document.getElementById(`ppPrice_${idx}`)?.value) || 0;
            const remark  = document.getElementById(`ppRowRemark_${idx}`)?.value || '';
            if (!matName || qty <= 0) return;
            items.push({ materialId: Number(matId), materialName: matName, spec, unit, quantity: qty, unitPrice: price, remark });
        });
    }

    if (!items.length) { showToast('请至少添加一条物料清单', 'error'); return; }

    const now    = _ppNow();
    const status = saveStatus || 'draft';

    if (id) {
        const plan = (window.mockData.purchasePlans || []).find(p => p.id === id);
        if (plan) {
            plan.planName  = planName;
            plan.orgId     = Number(orgId);
            plan.orgName   = orgName;
            plan.period    = document.getElementById('fPpPeriod')?.value || plan.period;
            plan.planDate  = planDate;
            plan.remark    = document.getElementById('fPpRemark')?.value || '';
            plan.items     = items;
            plan.updatedAt = now;
        }
    } else {
        const newPlan = {
            id:          (window.mockData.purchasePlans.length
                          ? Math.max(...window.mockData.purchasePlans.map(p => p.id)) + 1 : 1),
            planNo:      document.getElementById('fPpNo')?.value || _genPPNo(),
            planName, orgId: Number(orgId), orgName,
            period:      document.getElementById('fPpPeriod')?.value || 'week',
            planDate,
            remark:      document.getElementById('fPpRemark')?.value || '',
            status, items,
            creatorId:   1,
            creatorName: '张三',
            tenantId:    1,
            createdAt:   now.slice(0, 10),
            updatedAt:   now
        };
        window.mockData.purchasePlans.push(newPlan);
    }

    closeModal();
    showToast(status === 'submitted' ? '采购计划已提交' : '采购计划保存成功');
    _filterPurchasePlans();
}

/* ============================================================
   工具函数
   ============================================================ */
function _genPPNo() {
    const d   = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(Math.floor(Math.random() * 900 + 100));
    return `PP-${d}-${seq}`;
}

function _ppToday() { return new Date().toISOString().slice(0, 10); }

function _ppNow()   { return new Date().toLocaleString('sv-SE').replace('T', ' '); }
