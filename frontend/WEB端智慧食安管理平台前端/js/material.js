/**
 * material.js — 物料信息管理模块
 *
 * 功能：
 *  1. 查看物料列表 — 动态统计卡片、分页（10条/页）、按名称/分类/状态筛选、空状态提示
 *  2. 查看物料详情 — 详情弹窗展示全部字段
 *  3. 新增物料 — 完整表单、字段校验、提交后刷新列表
 *  4. 编辑物料 — 数据回填所有字段、保存后刷新列表
 */

/* ============================================================
   分页 & 搜索状态
   ============================================================ */
window.matPage        = 1;
window.matPageSize    = 10;
window.matFilteredList = null;   // null 表示未过滤，使用全量数据

/* ============================================================
   物料管理主页入口
   ============================================================ */
function renderMaterialPage(container) {
    window.matPage = 1;
    window.matFilteredList = null;
    _renderMaterial(container);
}

/* ============================================================
   内部渲染函数（统计卡片 + 工具栏 + 表格 + 分页）
   ============================================================ */
function _renderMaterial(container) {
    const all  = window.mockData.materials;
    const list = window.matFilteredList !== null ? window.matFilteredList : all;

    // 动态统计
    const total      = all.length;
    const warnCount  = all.filter(m => m.stockStatus === '库存不足').length;
    const nearExpiry = all.filter(m => {
        if (!m.expiryDate) return false;
        const diff = (new Date(m.expiryDate) - new Date()) / 86400000;
        return diff >= 0 && diff <= (m.warnDays || 7);
    }).length;
    const expired    = all.filter(m => m.stockStatus === '已过期').length;

    // 分页切片
    const page     = window.matPage;
    const pageSize = window.matPageSize;
    const total_p  = list.length;
    const pages    = Math.max(1, Math.ceil(total_p / pageSize));
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    // 保留搜索框当前值
    const savedName     = document.getElementById('matSearchName')     ? document.getElementById('matSearchName').value     : '';
    const savedCategory = document.getElementById('matSearchCategory') ? document.getElementById('matSearchCategory').value : '';
    const savedStatus   = document.getElementById('matSearchStatus')   ? document.getElementById('matSearchStatus').value   : '';

    container.innerHTML = `
        <!-- 统计卡片 -->
        <div class="stats-cards">
            <div class="stat-card">
                <div class="stat-card-title">物料总数</div>
                <div class="stat-card-value">${total}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">库存预警</div>
                <div class="stat-card-value" style="color:#e6a23c">${warnCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">即将过期</div>
                <div class="stat-card-value" style="color:#e6a23c">${nearExpiry}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">已过期</div>
                <div class="stat-card-value" style="color:#f56c6c">${expired}</div>
            </div>
        </div>

        <!-- 搜索工具栏 -->
        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="matSearchName" placeholder="物料名称" value="${savedName}">
                <select id="matSearchCategory">
                    <option value="">全部类别</option>
                    <option ${savedCategory==='蔬菜'?'selected':''}>蔬菜</option>
                    <option ${savedCategory==='肉类'?'selected':''}>肉类</option>
                    <option ${savedCategory==='水产'?'selected':''}>水产</option>
                    <option ${savedCategory==='调料'?'selected':''}>调料</option>
                    <option ${savedCategory==='粮油'?'selected':''}>粮油</option>
                    <option ${savedCategory==='乳制品'?'selected':''}>乳制品</option>
                </select>
                <select id="matSearchStatus">
                    <option value="">全部状态</option>
                    <option ${savedStatus==='正常'?'selected':''}>正常</option>
                    <option ${savedStatus==='库存不足'?'selected':''}>库存不足</option>
                    <option ${savedStatus==='库存积压'?'selected':''}>库存积压</option>
                    <option ${savedStatus==='已过期'?'selected':''}>已过期</option>
                </select>
                <button class="btn btn-primary" onclick="searchMaterials()">搜索</button>
                <button class="btn btn-default"  onclick="resetMaterialSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openMaterialModal()">+ 新增物料</button>
            </div>
        </div>

        <!-- 数据表格 -->
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>图片</th><th>物料名称</th><th>物料编码</th><th>规格</th>
                        <th>类别</th><th>保质期(天)</th><th>当前库存</th>
                        <th>库存范围</th><th>库存状态</th><th>操作</th>
                    </tr>
                </thead>
                <tbody id="matTableBody">
                    ${_renderMatRows(pageData)}
                </tbody>
            </table>

            <!-- 分页 -->
            <div class="pagination">
                <span style="color:#606266;font-size:14px">共 ${total_p} 条</span>
                <button class="btn btn-default" onclick="matChangePage(${page - 1})" ${page <= 1 ? 'disabled' : ''}>上一页</button>
                <span style="font-size:14px;color:#606266">${page} / ${pages}</span>
                <button class="btn btn-default" onclick="matChangePage(${page + 1})" ${page >= pages ? 'disabled' : ''}>下一页</button>
            </div>
        </div>
    `;
}

/* 渲染表格行 */
function _renderMatRows(list) {
    if (!list || list.length === 0) {
        return `<tr><td colspan="10" style="text-align:center;padding:40px;color:#909399">暂无数据</td></tr>`;
    }
    return list.map(m => {
        const rowClass = m.stockStatus === '已过期' ? 'danger-row'
                       : _isNearExpiry(m)           ? 'warning-row' : '';
        return `
        <tr class="${rowClass}">
            <td><span class="material-img">${m.emoji || '📦'}</span></td>
            <td>${m.name}</td>
            <td>${m.code}</td>
            <td>${m.spec}</td>
            <td>${m.category}</td>
            <td>${m.shelfLife}</td>
            <td>${m.stock} ${m.unit}</td>
            <td>${m.minStock}~${m.maxStock}</td>
            <td>${getStatusTag(m.stockStatus)}</td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showMaterialDetail(${m.id})">详情</button>
                    <button class="btn-link" onclick="openMaterialModal(${m.id})">编辑</button>
                    <button class="btn-link danger" onclick="deleteMaterial(${m.id})">删除</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

/* 判断是否临期 */
function _isNearExpiry(m) {
    if (!m.expiryDate) return false;
    const diff = (new Date(m.expiryDate) - new Date()) / 86400000;
    return diff >= 0 && diff <= (m.warnDays || 7);
}

/* ============================================================
   分页切换
   ============================================================ */
function matChangePage(p) {
    const list = window.matFilteredList !== null ? window.matFilteredList : window.mockData.materials;
    const pages = Math.max(1, Math.ceil(list.length / window.matPageSize));
    if (p < 1 || p > pages) return;
    window.matPage = p;
    _renderMaterial(document.getElementById('mainContent'));
}

/* ============================================================
   搜索 / 重置
   ============================================================ */
function searchMaterials() {
    const name     = (document.getElementById('matSearchName').value     || '').trim();
    const category = (document.getElementById('matSearchCategory').value || '').trim();
    const status   = (document.getElementById('matSearchStatus').value   || '').trim();

    window.matFilteredList = window.mockData.materials.filter(m => {
        if (name     && !m.name.includes(name))         return false;
        if (category && m.category !== category)         return false;
        if (status   && m.stockStatus !== status)        return false;
        return true;
    });
    window.matPage = 1;
    _renderMaterial(document.getElementById('mainContent'));
}

function resetMaterialSearch() {
    window.matFilteredList = null;
    window.matPage = 1;
    _renderMaterial(document.getElementById('mainContent'));
}

/* ============================================================
   删除物料
   ============================================================ */
function deleteMaterial(id) {
    if (!confirm('确认删除该物料？')) return;
    window.mockData.materials = window.mockData.materials.filter(m => m.id !== id);
    if (window.matFilteredList !== null) {
        window.matFilteredList = window.matFilteredList.filter(m => m.id !== id);
    }
    _renderMaterial(document.getElementById('mainContent'));
    showToast('删除成功');
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showMaterialDetail(id) {
    const m = window.mockData.materials.find(m => m.id === id);
    if (!m) return;

    const expiryInfo = m.expiryDate
        ? `${m.expiryDate}（剩余 ${Math.ceil((new Date(m.expiryDate) - new Date()) / 86400000)} 天）`
        : '—';

    showModal(`
        <div class="modal-header">
            <span class="modal-title">${m.emoji || '📦'} 物料详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">物料名称</span><span class="detail-value">${m.name}</span></div>
                    <div class="detail-item"><span class="detail-label">物料编码</span><span class="detail-value">${m.code}</span></div>
                    <div class="detail-item"><span class="detail-label">规格</span><span class="detail-value">${m.spec}</span></div>
                    <div class="detail-item"><span class="detail-label">单位</span><span class="detail-value">${m.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">类别</span><span class="detail-value">${m.category}</span></div>
                    <div class="detail-item"><span class="detail-label">保质期</span><span class="detail-value">${m.shelfLife} 天</span></div>
                    <div class="detail-item"><span class="detail-label">临期提醒天数</span><span class="detail-value">${m.expireRemindDays || '—'} 天</span></div>
                    <div class="detail-item"><span class="detail-label">预警天数</span><span class="detail-value">${m.warnDays || '—'} 天</span></div>
                    <div class="detail-item"><span class="detail-label">到期日期</span><span class="detail-value">${expiryInfo}</span></div>
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${m.createdAt || '—'}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">库存信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">当前库存</span><span class="detail-value">${m.stock} ${m.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">库存状态</span><span class="detail-value">${getStatusTag(m.stockStatus)}</span></div>
                    <div class="detail-item"><span class="detail-label">最低库存</span><span class="detail-value">${m.minStock} ${m.unit}</span></div>
                    <div class="detail-item"><span class="detail-label">最高库存</span><span class="detail-value">${m.maxStock} ${m.unit}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">存储要求 & 备注</div>
                <div class="detail-grid">
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">存储要��</span><span class="detail-value">${m.storageReq || '—'}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">备注</span><span class="detail-value">${m.remark || '—'}</span></div>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-primary" onclick="openMaterialModal(${m.id});closeModal()">编辑</button>
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '640px');
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openMaterialModal(id) {
    window.editingMaterialId = id || null;
    const m   = id ? window.mockData.materials.find(m => m.id === id) : null;
    const title = m ? '编辑物料' : '新增物料';
    const code  = m ? m.code : 'MAT-' + String(Date.now()).slice(-6);

    showModal(`
        <div class="modal-header">
            <span class="modal-title">${title}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body" style="overflow-x:hidden">
            <div class="form-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px;box-sizing:border-box;width:100%">
                <div class="form-item">
                    <label class="form-label required">物料名称</label>
                    <input class="form-input" id="fMatName" value="${m ? m.name : ''}" placeholder="请输入物料名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">物料编码</label>
                    <input class="form-input" id="fMatCode" value="${code}" ${m ? 'readonly' : ''}>
                </div>
                <div class="form-item">
                    <label class="form-label required">规格</label>
                    <input class="form-input" id="fMatSpec" value="${m ? m.spec : ''}" placeholder="如：500g/袋">
                </div>
                <div class="form-item">
                    <label class="form-label required">单位</label>
                    <input class="form-input" id="fMatUnit" value="${m ? m.unit : ''}" placeholder="如：袋、kg、桶">
                </div>
                <div class="form-item">
                    <label class="form-label required">类别</label>
                    <select class="form-select" id="fMatCategory">
                        ${['蔬菜','肉类','水产','调料','粮油','乳制品'].map(c =>
                            `<option ${m && m.category===c ? 'selected' : ''}>${c}</option>`
                        ).join('')}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label required">保质期（天）</label>
                    <input class="form-input" id="fMatShelfLife" type="number" min="1" value="${m ? m.shelfLife : ''}" placeholder="请输入天数">
                </div>
                <div class="form-item">
                    <label class="form-label">临期提醒天数</label>
                    <div style="display:flex;align-items:center;gap:8px">
                        <input class="form-input" id="fMatExpireRemind" type="number" min="1" value="${m ? (m.expireRemindDays||'') : ''}" placeholder="到期前几天提醒" style="flex:1">
                        <button class="ai-suggest-btn" onclick="aiSuggestExpireRemind(event)">AI建议</button>
                    </div>
                </div>
                <div class="form-item">
                    <label class="form-label">预警天数</label>
                    <input class="form-input" id="fMatWarnDays" type="number" min="1" value="${m ? (m.warnDays||'') : ''}" placeholder="库存预警天数">
                </div>
                <div class="form-item">
                    <label class="form-label required">最低库存</label>
                    <input class="form-input" id="fMatMinStock" type="number" min="0" value="${m ? m.minStock : ''}" placeholder="库存下限">
                </div>
                <div class="form-item">
                    <label class="form-label required">最高库存</label>
                    <input class="form-input" id="fMatMaxStock" type="number" min="0" value="${m ? m.maxStock : ''}" placeholder="库存上限">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">存储要求</label>
                    <input class="form-input" id="fMatStorageReq" value="${m ? (m.storageReq||'') : ''}" placeholder="如：冷藏 0-4℃">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">物料图片</label>
                    <div id="fMatImgWrap" style="display:flex;align-items:flex-start;gap:12px;flex-wrap:wrap">
                        <div id="fMatImgPreview" style="width:200px;height:150px;border:1px dashed #dcdfe6;border-radius:4px;display:flex;align-items:center;justify-content:center;overflow:hidden;background:#fafafa;flex-shrink:0">
                            ${m && m.imageUrl
                                ? `<img src="${m.imageUrl}" style="width:100%;height:100%;object-fit:cover">`
                                : `<span style="color:#c0c4cc;font-size:13px;text-align:center;padding:8px">暂无图片<br>点击上传</span>`
                            }
                        </div>
                        <div style="display:flex;flex-direction:column;gap:8px;justify-content:center">
                            <label class="btn btn-default" style="cursor:pointer;margin:0">
                                ${m && m.imageUrl ? '更换图片' : '上传图片'}
                                <input type="file" id="fMatImgInput" accept="image/*" style="display:none" onchange="onMatImgChange(this)">
                            </label>
                            <button class="btn btn-default" id="fMatImgDelBtn" onclick="clearMatImg()" style="${m && m.imageUrl ? '' : 'display:none'}">删除图片</button>
                            <span style="font-size:12px;color:#909399">支持 JPG/PNG/GIF，建议尺寸 200×150</span>
                        </div>
                    </div>
                </div>
                <div class="form-item span-2">
                    <label class="form-label">备注</label>
                    <textarea class="form-textarea" id="fMatRemark" placeholder="备注信息">${m ? (m.remark||'') : ''}</textarea>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveMaterial()">保存</button>
        </div>
    `, '720px');
}

/* ============================================================
   保存物料（新增 / 编辑）
   ============================================================ */
function saveMaterial() {
    const name = (document.getElementById('fMatName').value || '').trim();
    const code = (document.getElementById('fMatCode').value || '').trim();
    if (!name) { showToast('请输入物料名称', 'error'); return; }
    if (!code) { showToast('请输入物料编码', 'error'); return; }

    const shelfLife = parseInt(document.getElementById('fMatShelfLife').value) || 0;
    if (shelfLife <= 0) { showToast('请输入有效的保质期天数', 'error'); return; }

    const minStock = parseInt(document.getElementById('fMatMinStock').value) || 0;
    const maxStock = parseInt(document.getElementById('fMatMaxStock').value) || 0;
    if (maxStock < minStock) { showToast('最高库存不能小于最低库存', 'error'); return; }

    const fields = {
        name,
        code,
        spec:             document.getElementById('fMatSpec').value.trim(),
        unit:             document.getElementById('fMatUnit').value.trim(),
        category:         document.getElementById('fMatCategory').value,
        shelfLife,
        expireRemindDays: parseInt(document.getElementById('fMatExpireRemind').value) || null,
        warnDays:         parseInt(document.getElementById('fMatWarnDays').value)     || null,
        minStock,
        maxStock,
        storageReq:       document.getElementById('fMatStorageReq').value.trim(),
        remark:           document.getElementById('fMatRemark').value.trim(),
        imageUrl:         window._matImgDataUrl || (m ? (m.imageUrl||null) : null),
    };

    if (window.editingMaterialId) {
        const idx = window.mockData.materials.findIndex(m => m.id === window.editingMaterialId);
        if (idx !== -1) {
            window.mockData.materials[idx] = Object.assign({}, window.mockData.materials[idx], fields);
        }
    } else {
        window.mockData.materials.push(Object.assign({
            id:          Date.now(),
            emoji:       '📦',
            stock:       0,
            stockStatus: '正常',
            expiryDate:  null,
            createdAt:   new Date().toLocaleString('zh-CN')
        }, fields));
    }

    // 重置过滤，刷新列表
    window.matFilteredList = null;
    window.matPage = 1;
    closeModal();
    _renderMaterial(document.getElementById('mainContent'));
    showToast(window.editingMaterialId ? '编辑成功' : '新增成功');
}

/* ============================================================
   物料图片上传 / 更换 / 删除
   ============================================================ */
window._matImgDataUrl = null;

function onMatImgChange(input) {
    if (!input.files || !input.files[0]) return;
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = function(e) {
        window._matImgDataUrl = e.target.result;
        const preview = document.getElementById('fMatImgPreview');
        if (preview) {
            preview.innerHTML = `<img src="${e.target.result}" style="width:100%;height:100%;object-fit:cover">`;
        }
        // 显示删除按钮，更新上传按钮文字
        const delBtn = document.getElementById('fMatImgDelBtn');
        if (delBtn) delBtn.style.display = '';
        const label = input.closest('label');
        if (label) label.childNodes[0].textContent = '更换图片';
        showToast('图片已选择，保存后生效');
    };
    reader.readAsDataURL(file);
}

function clearMatImg() {
    window._matImgDataUrl = '';
    const preview = document.getElementById('fMatImgPreview');
    if (preview) {
        preview.innerHTML = `<span style="color:#c0c4cc;font-size:13px;text-align:center;padding:8px">暂无图片<br>点击上传</span>`;
    }
    const delBtn = document.getElementById('fMatImgDelBtn');
    if (delBtn) delBtn.style.display = 'none';
    const input = document.getElementById('fMatImgInput');
    if (input) input.value = '';
    showToast('图片已删除');
}

/* ============================================================
   临期提醒天数 AI 建议
   Mock 规则（对齐食品安全标准）：
     保质期 ≤ 3天   → 推荐 1天
     保质期 ≤ 7天   → 推荐 2天
     保质期 ≤ 30天  → 推荐 3天
     保质期 ≤ 90天  → 推荐 7天
     保质期 ≤ 180天 → 推荐 15天
     保质期 ≤ 365天 → 推荐 30天
     保质期 > 365天 → 推荐 60天
   冷藏/冷冻存储要求额外缩短提醒周期（×0.8，取整）
   ============================================================ */
function aiSuggestExpireRemind(event) {
    const shelfLife  = parseInt(document.getElementById('fMatShelfLife').value) || 0;
    const storageReq = (document.getElementById('fMatStorageReq').value || '').toLowerCase();
    const name       = (document.getElementById('fMatName').value || '').trim();

    if (!shelfLife || shelfLife <= 0) {
        showAiTooltip('请先填写保质期天数，再获取AI建议', event);
        return;
    }

    // 基础推荐天数
    let suggest;
    if      (shelfLife <= 3)   suggest = 1;
    else if (shelfLife <= 7)   suggest = 2;
    else if (shelfLife <= 30)  suggest = 3;
    else if (shelfLife <= 90)  suggest = 7;
    else if (shelfLife <= 180) suggest = 15;
    else if (shelfLife <= 365) suggest = 30;
    else                       suggest = 60;

    // 冷藏/冷冻存储适当缩短
    const isCold = storageReq.includes('冷藏') || storageReq.includes('冷冻') || storageReq.includes('冷');
    if (isCold && suggest > 1) suggest = Math.max(1, Math.floor(suggest * 0.8));

    document.getElementById('fMatExpireRemind').value = suggest;

    const reason = isCold
        ? `冷藏/冷冻食品（保质期${shelfLife}天），建议提前 ${suggest} 天提醒，确保及时处理`
        : `保质期${shelfLife}天，依据食品安全标准建议提前 ${suggest} 天提醒`;

    showAiTooltip(`已为您推荐合理的临期提醒天数：${suggest} 天\n${reason}`, event);
}
