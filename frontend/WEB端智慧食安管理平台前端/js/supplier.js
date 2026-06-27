/**
 * supplier.js — 供应商管理模块
 *
 * 运行验证说明：
 *  1. 双击 index.html，点击左侧「采购管理」分组下的「供应商管理」进入
 *  2. 验证【查看列表】：展示5条Mock供应商数据，统计卡片动态计算
 *  3. 验证【筛选】：名称输入"鲜达"→搜索只显示鲜达农产品；选"待审核"→只显示待审核；点重置恢复
 *  4. 验证【详情】：点任意行「详情」→弹窗展示供应商全部字段含资质信息、AI评分等
 *  5. 验证【新增】：点「+ 新增供应商」→填写名称+编码+联系人→保存→列表新增，Toast"新增成功"，状态自动为"待审核"
 *  6. 验证【编辑】：点任意行「编辑」→弹窗精准回显所有字段→修改后保存→列表同步更新
 *  7. 验证【必填校验】：新增时不填名称直接提交→Toast"请输入供应商名称"
 *  8. 字段核对：对照 scm_supplier 表字段：supplier_code/supplier_name/contact_name/contact_phone/
 *     contact_email/address/category_tags/license_no/license_expires_at/food_license_no/
 *     food_license_expires_at/credit_score/status
 *
 * 核心改动点：
 *  - 新增供应商管理导航入口（sidebar.js renderPage 新增 supplier 分支）
 *  - 新增供应商表单DOM（对齐 scm_supplier 表字段）
 *  - 编辑供应商数据回显逻辑（所有字段精准回填）
 *  - 供应商列表筛选功能（按名称/编码/状态筛选，分页10条/页）
 *  - 供应商详情弹窗（showSupplierDetail，展示全部字段含资质、评分）
 */

/* ============================================================
   分页 & 搜索状态
   ============================================================ */
window.supPage         = 1;
window.supPageSize     = 10;
window.supFilteredList = null;

/* ============================================================
   供应商管理主页入口
   ============================================================ */
function renderSupplierPage(container) {
    window.supPage = 1;
    window.supFilteredList = null;
    _renderSupplier(container);
}

/* ============================================================
   状态映射
   ============================================================ */
const SUP_STATUS_MAP = {
    pending:   { label: '待审核', cls: 'tag-warning' },
    active:    { label: '已审核', cls: 'tag-success' },
    rejected:  { label: '已驳回', cls: 'tag-danger'  },
    disabled:  { label: '禁用',   cls: 'tag-info'    },
    cancelled: { label: '已注销', cls: 'tag-info'    }
};

function _supStatusTag(status) {
    const s = SUP_STATUS_MAP[status] || { label: status, cls: 'tag-info' };
    return `<span class="tag ${s.cls}">${s.label}</span>`;
}

/* ============================================================
   内部渲染函数
   ============================================================ */
function _renderSupplier(container) {
    const all  = window.mockData.suppliers;
    const list = window.supFilteredList !== null ? window.supFilteredList : all;

    const total     = all.length;
    const active    = all.filter(s => s.status === 'active').length;
    const pending   = all.filter(s => s.status === 'pending').length;
    const nearExp   = all.filter(s => {
        if (!s.licenseExpiresAt) return false;
        const diff = (new Date(s.licenseExpiresAt) - new Date()) / 86400000;
        return diff >= 0 && diff <= 30;
    }).length;

    const page     = window.supPage;
    const pageSize = window.supPageSize;
    const total_p  = list.length;
    const pages    = Math.max(1, Math.ceil(total_p / pageSize));
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    const savedName   = document.getElementById('supSearchName')   ? document.getElementById('supSearchName').value   : '';
    const savedCode   = document.getElementById('supSearchCode')   ? document.getElementById('supSearchCode').value   : '';
    const savedStatus = document.getElementById('supSearchStatus') ? document.getElementById('supSearchStatus').value : '';

    container.innerHTML = `
        <div class="stats-cards">
            <div class="stat-card">
                <div class="stat-card-title">供应商总数</div>
                <div class="stat-card-value">${total}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">已审核</div>
                <div class="stat-card-value" style="color:#67c23a">${active}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">待审核</div>
                <div class="stat-card-value" style="color:#e6a23c">${pending}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">资质30天内到期</div>
                <div class="stat-card-value" style="color:#f56c6c">${nearExp}</div>
            </div>
        </div>

        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="supSearchName" placeholder="供应商名称" value="${savedName}">
                <input type="text" id="supSearchCode" placeholder="供应商编码" value="${savedCode}" style="width:160px">
                <select id="supSearchStatus">
                    <option value="">全部状态</option>
                    <option ${savedStatus==='pending'  ?'selected':''} value="pending">待审核</option>
                    <option ${savedStatus==='active'   ?'selected':''} value="active">已审核</option>
                    <option ${savedStatus==='rejected' ?'selected':''} value="rejected">已驳回</option>
                    <option ${savedStatus==='disabled' ?'selected':''} value="disabled">禁用</option>
                    <option ${savedStatus==='cancelled'?'selected':''} value="cancelled">已注销</option>
                </select>
                <button class="btn btn-primary" onclick="searchSuppliers()">搜索</button>
                <button class="btn btn-default"  onclick="resetSupplierSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openSupplierModal()">+ 新增供应商</button>
            </div>
        </div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>供应商名称</th><th>供应商编码</th><th>联系人</th>
                        <th>联系电话</th><th>供应品类</th><th>信用评分</th>
                        <th>营业执照到期</th><th>状态</th><th>操作</th>
                    </tr>
                </thead>
                <tbody>${_renderSupplierRows(pageData)}</tbody>
            </table>
            <div class="pagination">
                <span style="color:#606266;font-size:14px">共 ${total_p} 条</span>
                <button class="btn btn-default" onclick="supChangePage(${page-1})" ${page<=1?'disabled':''}>上一页</button>
                <span style="font-size:14px;color:#606266">${page} / ${pages}</span>
                <button class="btn btn-default" onclick="supChangePage(${page+1})" ${page>=pages?'disabled':''}>下一页</button>
            </div>
        </div>
    `;
}

/* 渲染表格行 */
function _renderSupplierRows(list) {
    if (!list || list.length === 0) {
        return `<tr><td colspan="9" style="text-align:center;padding:40px;color:#909399">暂无数据</td></tr>`;
    }
    return list.map(s => {
        const tags = (s.categoryTags || []).map(t => `<span class="tag tag-primary" style="margin:1px">${t}</span>`).join('');
        const expDate = s.licenseExpiresAt || '—';
        const expStyle = s.licenseExpiresAt && (new Date(s.licenseExpiresAt) - new Date()) / 86400000 <= 30
            ? 'color:#f56c6c;font-weight:600' : '';
        const scoreColor = s.creditScore >= 90 ? '#67c23a' : s.creditScore >= 70 ? '#e6a23c' : '#f56c6c';
        return `<tr>
            <td><strong>${s.supplierName}</strong></td>
            <td>${s.supplierCode}</td>
            <td>${s.contactName || '—'}</td>
            <td>${s.contactPhone || '—'}</td>
            <td>${tags || '—'}</td>
            <td><span style="color:${scoreColor};font-weight:600">${s.creditScore}</span></td>
            <td><span style="${expStyle}">${expDate}</span></td>
            <td>${_supStatusTag(s.status)}</td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showSupplierDetail(${s.id})">详情</button>
                    <button class="btn-link" onclick="openSupplierModal(${s.id})">编辑</button>
                    <button class="btn-link danger" onclick="cancelSupplier(${s.id})">注销</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

/* ============================================================
   分页 / 搜索 / 重置 / 注销
   ============================================================ */
function supChangePage(p) {
    const list  = window.supFilteredList !== null ? window.supFilteredList : window.mockData.suppliers;
    const pages = Math.max(1, Math.ceil(list.length / window.supPageSize));
    if (p < 1 || p > pages) return;
    window.supPage = p;
    _renderSupplier(document.getElementById('mainContent'));
}

function searchSuppliers() {
    const name   = (document.getElementById('supSearchName').value   || '').trim();
    const code   = (document.getElementById('supSearchCode').value   || '').trim();
    const status = (document.getElementById('supSearchStatus').value || '').trim();
    window.supFilteredList = window.mockData.suppliers.filter(s => {
        if (name   && !s.supplierName.includes(name))   return false;
        if (code   && !s.supplierCode.includes(code))   return false;
        if (status && s.status !== status)               return false;
        return true;
    });
    window.supPage = 1;
    _renderSupplier(document.getElementById('mainContent'));
}

function resetSupplierSearch() {
    window.supFilteredList = null;
    window.supPage = 1;
    _renderSupplier(document.getElementById('mainContent'));
}

function cancelSupplier(id) {
    const s = window.mockData.suppliers.find(x => x.id === id);
    if (!s) return;
    if (s.status === 'cancelled') { showToast('该供应商��注销', 'error'); return; }
    if (!confirm('确认注销该供应商？注销后不可恢复。')) return;
    s.status = 'cancelled';
    s.updatedAt = new Date().toLocaleString('zh-CN');
    if (window.supFilteredList !== null) {
        const idx = window.supFilteredList.findIndex(x => x.id === id);
        if (idx !== -1) window.supFilteredList[idx] = s;
    }
    _renderSupplier(document.getElementById('mainContent'));
    showToast('注销成功');
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showSupplierDetail(id) {
    const s = window.mockData.suppliers.find(x => x.id === id);
    if (!s) return;

    const tags = (s.categoryTags || []).map(t => `<span class="tag tag-primary" style="margin:2px">${t}</span>`).join('') || '—';
    const scoreColor = s.creditScore >= 90 ? '#67c23a' : s.creditScore >= 70 ? '#e6a23c' : '#f56c6c';

    showModal(`
        <div class="modal-header">
            <span class="modal-title">🏢 供应商详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">供应商名称</span><span class="detail-value">${s.supplierName}</span></div>
                    <div class="detail-item"><span class="detail-label">供应商编码</span><span class="detail-value">${s.supplierCode}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value">${_supStatusTag(s.status)}</span></div>
                    <div class="detail-item"><span class="detail-label">信用评分</span><span class="detail-value" style="color:${scoreColor};font-weight:600">${s.creditScore}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">供应品类</span><span class="detail-value">${tags}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">地址</span><span class="detail-value">${s.address || '—'}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">联系信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">联系人</span><span class="detail-value">${s.contactName || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">联系电话</span><span class="detail-value">${s.contactPhone || '—'}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">联系邮箱</span><span class="detail-value">${s.contactEmail || '—'}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">资质信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">营业执照编号</span><span class="detail-value">${s.licenseNo || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">营业执照到期</span><span class="detail-value">${s.licenseExpiresAt || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">食品经营许可证编号</span><span class="detail-value">${s.foodLicenseNo || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">食品许可证到期</span><span class="detail-value">${s.foodLicenseExpiresAt || '—'}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">AI综合评分</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">综合评分</span><span class="detail-value" style="color:${scoreColor};font-weight:600;font-size:18px">${s.creditScore}</span></div>
                    <div class="detail-item"><span class="detail-label">资质完整性</span><span class="detail-value">${s.scoreQualification || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">历史供货质量</span><span class="detail-value">${s.scoreQuality || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">价格稳定性</span><span class="detail-value">${s.scorePrice || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">履约准时率</span><span class="detail-value">${s.scoreDelivery || '—'}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">时间信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${s.createdAt}</span></div>
                    <div class="detail-item"><span class="detail-label">更新时间</span><span class="detail-value">${s.updatedAt || '—'}</span></div>
                    ${s.auditAt ? `<div class="detail-item"><span class="detail-label">审核时间</span><span class="detail-value">${s.auditAt}</span></div>` : ''}
                    ${s.auditRemark ? `<div class="detail-item"><span class="detail-label">审核备注</span><span class="detail-value">${s.auditRemark}</span></div>` : ''}
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-primary" onclick="openSupplierModal(${s.id});closeModal()">编辑</button>
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '680px');
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openSupplierModal(id) {
    window.editingSupplierId = id || null;
    const s     = id ? window.mockData.suppliers.find(x => x.id === id) : null;
    const title = s ? '编辑供应商' : '新增供应商';
    const cats  = ['蔬菜', '肉类', '水产', '调料', '粮油', '乳制品', '饮料', '冷冻食品'];
    const catChecks = cats.map(c => {
        const checked = s && (s.categoryTags || []).includes(c) ? 'checked' : '';
        return `<label style="margin-right:12px;font-size:13px"><input type="checkbox" class="sup-cat-check" value="${c}" ${checked}> ${c}</label>`;
    }).join('');

    showModal(`
        <div class="modal-header">
            <span class="modal-title">${title}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="form-grid">
                <div class="form-item">
                    <label class="form-label required">供应商名称</label>
                    <input class="form-input" id="fSupName" value="${s ? s.supplierName : ''}" placeholder="请输入供应商名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">供应商编码</label>
                    <input class="form-input" id="fSupCode" value="${s ? s.supplierCode : ''}" placeholder="如：SUP-001" ${s ? 'readonly' : ''}>
                </div>
                <div class="form-item">
                    <label class="form-label required">联系人</label>
                    <input class="form-input" id="fSupContact" value="${s ? (s.contactName || '') : ''}" placeholder="请输入联系人姓名">
                </div>
                <div class="form-item">
                    <label class="form-label">联系电话</label>
                    <input class="form-input" id="fSupPhone" value="${s ? (s.contactPhone || '') : ''}" placeholder="请输入联系电话">
                </div>
                <div class="form-item">
                    <label class="form-label">联系邮箱</label>
                    <input class="form-input" id="fSupEmail" value="${s ? (s.contactEmail || '') : ''}" placeholder="请输入联系邮箱">
                </div>
                <div class="form-item">
                    <label class="form-label">合作状态</label>
                    <select class="form-select" id="fSupStatus">
                        <option value="pending"   ${!s || s.status==='pending'   ? 'selected' : ''}>待审核</option>
                        <option value="active"    ${s && s.status==='active'    ? 'selected' : ''}>已审核</option>
                        <option value="rejected"  ${s && s.status==='rejected'  ? 'selected' : ''}>已驳回</option>
                        <option value="disabled"  ${s && s.status==='disabled'  ? 'selected' : ''}>禁用</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">营业执照编号</label>
                    <input class="form-input" id="fSupLicenseNo" value="${s ? (s.licenseNo || '') : ''}" placeholder="请输入营业执照编号">
                </div>
                <div class="form-item">
                    <label class="form-label">营业执照到期日</label>
                    <input class="form-input" type="date" id="fSupLicenseExp" value="${s ? (s.licenseExpiresAt || '') : ''}">
                </div>
                <div class="form-item">
                    <label class="form-label">食品经营许可证编号</label>
                    <input class="form-input" id="fSupFoodLicenseNo" value="${s ? (s.foodLicenseNo || '') : ''}" placeholder="请输入食品经营许可证编号">
                </div>
                <div class="form-item">
                    <label class="form-label">食品许可证到期日</label>
                    <input class="form-input" type="date" id="fSupFoodLicenseExp" value="${s ? (s.foodLicenseExpiresAt || '') : ''}">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">地址</label>
                    <input class="form-input" id="fSupAddress" value="${s ? (s.address || '') : ''}" placeholder="请输入供应商地址">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">供应品类</label>
                    <div style="padding:8px 0;line-height:2">${catChecks}</div>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveSupplier()">保存</button>
        </div>
    `, '720px');
}

/* ============================================================
   保存供应商（新增 / 编辑）
   ============================================================ */
function saveSupplier() {
    const supplierName = (document.getElementById('fSupName').value    || '').trim();
    const supplierCode = (document.getElementById('fSupCode').value    || '').trim();
    const contactName  = (document.getElementById('fSupContact').value || '').trim();
    if (!supplierName) { showToast('请输入供应商名称', 'error'); return; }
    if (!supplierCode) { showToast('请输入供应商编码', 'error'); return; }
    if (!contactName)  { showToast('请输入联系人',     'error'); return; }

    if (!window.editingSupplierId) {
        const exists = window.mockData.suppliers.some(s => s.supplierCode === supplierCode);
        if (exists) { showToast('供应商编码已存在，请更换', 'error'); return; }
    }

    const categoryTags = Array.from(document.querySelectorAll('.sup-cat-check:checked')).map(el => el.value);

    const fields = {
        supplierName,
        supplierCode,
        contactName,
        contactPhone:        document.getElementById('fSupPhone').value.trim(),
        contactEmail:        document.getElementById('fSupEmail').value.trim(),
        address:             document.getElementById('fSupAddress').value.trim(),
        licenseNo:           document.getElementById('fSupLicenseNo').value.trim(),
        licenseExpiresAt:    document.getElementById('fSupLicenseExp').value || null,
        foodLicenseNo:       document.getElementById('fSupFoodLicenseNo').value.trim(),
        foodLicenseExpiresAt:document.getElementById('fSupFoodLicenseExp').value || null,
        status:              document.getElementById('fSupStatus').value,
        categoryTags,
        updatedAt:           new Date().toLocaleString('zh-CN')
    };

    if (window.editingSupplierId) {
        const idx = window.mockData.suppliers.findIndex(s => s.id === window.editingSupplierId);
        if (idx !== -1) {
            window.mockData.suppliers[idx] = Object.assign({}, window.mockData.suppliers[idx], fields);
        }
    } else {
        window.mockData.suppliers.unshift(Object.assign({
            id:          Date.now(),
            creditScore: 100,
            scoreQualification: null,
            scoreQuality:       null,
            scorePrice:         null,
            scoreDelivery:      null,
            tenantId:    1,
            createdAt:   new Date().toLocaleString('zh-CN')
        }, fields));
    }

    window.supFilteredList = null;
    window.supPage = 1;
    closeModal();
    _renderSupplier(document.getElementById('mainContent'));
    showToast(window.editingSupplierId ? '编辑成功' : '新增成功');
}
