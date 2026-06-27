/**
 * recipe.js — 菜谱库管理模块
 *
 * 核心改动点：
 *  1. 新增菜谱表单DOM — 对齐 recipe_menu + recipe_menu_ingredient 表字段
 *  2. 编辑菜谱数据回显逻辑 — 所有字段精准回填，含食材行
 *  3. 菜谱列表筛选功能 — 按名称/分类筛选，分页（10条/页），空状态提示
 *  4. 菜谱详情弹窗 — showRecipeDetail(id)，展示全部字段含食材清单、烹饪步骤
 *  5. 食材清单关联逻辑 — 下拉选择物料表中的物料，自动带入规格/单位
 *
 * 运行验证说明：
 *  1. 双击 index.html，点击左侧「菜谱库管理」进入菜谱版块
 *  2. 验证【查看列表】：列表展示6条Mock数据，统计卡片数值动态计算
 *  3. 验证【筛选】：输入"红烧"点搜索 → 只显示红烧肉；选"素菜"→ 只显示素菜；点重置恢复
 *  4. 验证【分页】：Mock数据6条，分页显示"共6条 1/1页"
 *  5. 验证【详情】：点任意行「详情」→ 弹窗展示菜谱名称、分类、营养成分、烹饪参数、食材清单、制作步骤
 *  6. 验证【新增】：点「+ 新增菜谱」→ 填写名称+类别+食材 → 点确认 → 列表新增一条，Toast提示"新增成功"
 *  7. 验证【编辑】：点任意行「编辑」→ 弹窗回显所有字段含食材行 → 修改后保存 → 列表同步更新
 *  8. 验证【必填校验】：新增时不填名称直接提交 → Toast提示"请填写菜谱名称"
 *  9. 字段核对：对照 recipe_menu 表字段：menu_name/menu_category/cooking_time/cooking_temp_min/cooking_steps/calories/protein/carbohydrate/fat
 */

/* ============================================================
   分页 & 搜索状态
   ============================================================ */
window.recPage        = 1;
window.recPageSize    = 10;
window.recFilteredList = null;

/* ============================================================
   菜谱管理主页入口
   ============================================================ */
function renderRecipePage(container) {
    window.recPage = 1;
    window.recFilteredList = null;
    _renderRecipe(container);
}

/* ============================================================
   内部渲染函数（统计卡片 + 工具栏 + 表格 + 分页）
   ============================================================ */
function _renderRecipe(container) {
    const all  = window.mockData.recipes;
    const list = window.recFilteredList !== null ? window.recFilteredList : all;

    // 动态统计
    const total      = all.length;
    const weekNew    = all.filter(r => r.isNew).length;
    const categories = [...new Set(all.map(r => r.category))].length;
    const avgScore   = all.length
        ? Math.round(all.reduce((s, r) => s + (r.nutritionScore || 80), 0) / all.length)
        : 0;

    // 分页
    const page     = window.recPage;
    const pageSize = window.recPageSize;
    const total_p  = list.length;
    const pages    = Math.max(1, Math.ceil(total_p / pageSize));
    const start    = (page - 1) * pageSize;
    const pageData = list.slice(start, start + pageSize);

    // 保留搜索框值
    const savedName     = document.getElementById('recSearchName')     ? document.getElementById('recSearchName').value     : '';
    const savedCategory = document.getElementById('recSearchCategory') ? document.getElementById('recSearchCategory').value : '';

    container.innerHTML = `
        <!-- 统计卡片 -->
        <div class="stats-cards">
            <div class="stat-card">
                <div class="stat-card-title">菜谱总数</div>
                <div class="stat-card-value">${total}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">本周新增</div>
                <div class="stat-card-value" style="color:#67c23a">${weekNew}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">覆盖分类</div>
                <div class="stat-card-value" style="color:#409eff">${categories}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card-title">平均营养评分</div>
                <div class="stat-card-value" style="color:#e6a23c">${avgScore}</div>
            </div>
        </div>

        <!-- 搜索工具栏 -->
        <div class="toolbar">
            <div class="toolbar-row">
                <input type="text" id="recSearchName" placeholder="菜谱名称" value="${savedName}">
                <select id="recSearchCategory">
                    <option value="">全部类别</option>
                    <option ${savedCategory==='主食'?'selected':''}>主食</option>
                    <option ${savedCategory==='荤菜'?'selected':''}>荤菜</option>
                    <option ${savedCategory==='素菜'?'selected':''}>素菜</option>
                    <option ${savedCategory==='汤类'?'selected':''}>汤类</option>
                    <option ${savedCategory==='点心'?'selected':''}>点心</option>
                </select>
                <button class="btn btn-primary" onclick="searchRecipes()">搜索</button>
                <button class="btn btn-default"  onclick="resetRecipeSearch()">重置</button>
                <button class="btn btn-primary toolbar-right" onclick="openRecipeModal()">+ 新增菜谱</button>
            </div>
        </div>

        <!-- 数据表格 -->
        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>菜谱名称</th><th>菜谱类别</th><th>烹饪时长</th>
                        <th>目标温度</th><th>营养成分</th><th>营养评分</th><th>更新时间</th><th>操作</th>
                    </tr>
                </thead>
                <tbody id="recTableBody">
                    ${_renderRecipeRows(pageData)}
                </tbody>
            </table>
            <div class="pagination">
                <span style="color:#606266;font-size:14px">共 ${total_p} 条</span>
                <button class="btn btn-default" onclick="recChangePage(${page-1})" ${page<=1?'disabled':''}>上一页</button>
                <span style="font-size:14px;color:#606266">${page} / ${pages}</span>
                <button class="btn btn-default" onclick="recChangePage(${page+1})" ${page>=pages?'disabled':''}>下一页</button>
            </div>
        </div>
    `;
}

/* 渲染表格行 */
function _renderRecipeRows(list) {
    if (!list || list.length === 0) {
        return `<tr><td colspan="8" style="text-align:center;padding:40px;color:#909399">暂无数据</td></tr>`;
    }
    return list.map(r => `
        <tr>
            <td>${r.name}</td>
            <td>${getStatusTag(r.category)}</td>
            <td>${r.cookTime} 分钟</td>
            <td>${r.temperature} ℃</td>
            <td style="font-size:13px">
                热量:${r.calories}kcal 蛋白质:${r.protein}g<br>
                碳水:${r.carbs}g 脂肪:${r.fat}g
            </td>
            <td><span style="color:#e6a23c;font-weight:600">${r.nutritionScore || 80}</span></td>
            <td>${r.updateTime}</td>
            <td>
                <div class="action-btns">
                    <button class="btn-link" onclick="showRecipeDetail(${r.id})">详情</button>
                    <button class="btn-link" onclick="openRecipeModal(${r.id})">编辑</button>
                    <button class="btn-link danger" onclick="deleteRecipe(${r.id})">删除</button>
                </div>
            </td>
        </tr>`).join('');
}

/* ============================================================
   分页切换
   ============================================================ */
function recChangePage(p) {
    const list  = window.recFilteredList !== null ? window.recFilteredList : window.mockData.recipes;
    const pages = Math.max(1, Math.ceil(list.length / window.recPageSize));
    if (p < 1 || p > pages) return;
    window.recPage = p;
    _renderRecipe(document.getElementById('mainContent'));
}

/* ============================================================
   搜索 / 重置
   ============================================================ */
function searchRecipes() {
    const name     = (document.getElementById('recSearchName').value     || '').trim();
    const category = (document.getElementById('recSearchCategory').value || '').trim();

    window.recFilteredList = window.mockData.recipes.filter(r => {
        if (name     && !r.name.includes(name))     return false;
        if (category && r.category !== category)     return false;
        return true;
    });
    window.recPage = 1;
    _renderRecipe(document.getElementById('mainContent'));
}

function resetRecipeSearch() {
    window.recFilteredList = null;
    window.recPage = 1;
    _renderRecipe(document.getElementById('mainContent'));
}

/* ============================================================
   删除菜谱
   ============================================================ */
function deleteRecipe(id) {
    if (!confirm('确认删除该菜谱？')) return;
    window.mockData.recipes = window.mockData.recipes.filter(r => r.id !== id);
    if (window.recFilteredList !== null) {
        window.recFilteredList = window.recFilteredList.filter(r => r.id !== id);
    }
    _renderRecipe(document.getElementById('mainContent'));
    showToast('删除成功');
}
/* ============================================================
   详情弹窗
   ============================================================ */
function showRecipeDetail(id) {
    const r = window.mockData.recipes.find(r => r.id === id);
    if (!r) return;

    const ingredientRows = (r.ingredients || []).map(ing => `
        <tr>
            <td style="padding:8px;border-bottom:1px solid #ebeef5">${ing.materialName}</td>
            <td style="padding:8px;border-bottom:1px solid #ebeef5">${ing.spec || '—'}</td>
            <td style="padding:8px;border-bottom:1px solid #ebeef5">${ing.quantity} ${ing.unit}</td>
            <td style="padding:8px;border-bottom:1px solid #ebeef5">${ing.isMain ? '<span class="tag tag-primary">主料</span>' : '<span class="tag tag-info">辅料</span>'}</td>
            <td style="padding:8px;border-bottom:1px solid #ebeef5">${ing.remark || '—'}</td>
        </tr>`).join('') || '<tr><td colspan="5" style="padding:16px;text-align:center;color:#909399">暂无食材数据</td></tr>';

    const steps = (r.cookingSteps || []).map((s, i) =>
        `<div style="margin-bottom:10px"><span style="color:#409eff;font-weight:600">步骤${i+1}：</span>${s}</div>`
    ).join('') || `<div style="color:#909399">${r.method || '暂无制作步骤'}</div>`;

    showModal(`
        <div class="modal-header">
            <span class="modal-title">🍽️ 菜谱详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">菜谱名称</span><span class="detail-value">${r.name}</span></div>
                    <div class="detail-item"><span class="detail-label">菜谱类别</span><span class="detail-value">${r.category}</span></div>
                    <div class="detail-item"><span class="detail-label">目标烹饪时长</span><span class="detail-value">${r.cookTime} 分钟</span></div>
                    <div class="detail-item"><span class="detail-label">目标温度</span><span class="detail-value">${r.temperature} ℃</span></div>
                    <div class="detail-item"><span class="detail-label">营养评分</span><span class="detail-value" style="color:#e6a23c;font-weight:600">${r.nutritionScore || 80}</span></div>
                    <div class="detail-item"><span class="detail-label">更新时间</span><span class="detail-value">${r.updateTime}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">营养成分（每100g）</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">热量</span><span class="detail-value">${r.calories} kcal</span></div>
                    <div class="detail-item"><span class="detail-label">蛋白质</span><span class="detail-value">${r.protein} g</span></div>
                    <div class="detail-item"><span class="detail-label">碳水化合物</span><span class="detail-value">${r.carbs} g</span></div>
                    <div class="detail-item"><span class="detail-label">脂肪</span><span class="detail-value">${r.fat} g</span></div>
                    <div class="detail-item"><span class="detail-label">钠</span><span class="detail-value">${r.sodium || '—'} mg</span></div>
                    <div class="detail-item"><span class="detail-label">膳食纤维</span><span class="detail-value">${r.fiber || '—'} g</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">所需食材</div>
                <table style="width:100%;border-collapse:collapse;font-size:13px">
                    <thead style="background:#f5f7fa">
                        <tr>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">食材名称</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">规格</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">用量</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">类型</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">备注</th>
                        </tr>
                    </thead>
                    <tbody>${ingredientRows}</tbody>
                </table>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">制作步骤</div>
                <div style="font-size:14px;color:#606266;line-height:1.8">${steps}</div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-primary" onclick="openRecipeModal(${r.id});closeModal()">编辑</button>
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '720px');
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openRecipeModal(id) {
    window.editingRecipeId = id || null;
    const r = id ? window.mockData.recipes.find(x => x.id === id) : null;
    const title = r ? '编辑菜谱' : '新增菜谱';

    // 构建食材行（编辑时回显，新增时给一行默认空行）
    const defaultIngRows = r && r.ingredients && r.ingredients.length
        ? r.ingredients.map(ing => _buildIngredientRow(ing)).join('')
        : _buildIngredientRow(null);

    showModal(`
        <div class="modal-header">
            <span class="modal-title">${title}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="form-grid">
                <div class="form-item">
                    <label class="form-label required">菜谱名称</label>
                    <input class="form-input" id="fRecName" value="${r ? r.name : ''}" placeholder="请输入菜谱名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">菜谱类别</label>
                    <select class="form-select" id="fRecCategory">
                        ${['主食','荤菜','素菜','汤类','点心'].map(c =>
                            `<option ${r && r.category===c ? 'selected' : ''}>${c}</option>`
                        ).join('')}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">目标烹饪时长（分钟）</label>
                    <div style="display:flex;align-items:center;gap:8px">
                        <input class="form-input" type="number" id="fRecCookTime" value="${r ? r.cookTime : ''}" placeholder="请输入烹饪时长" style="flex:1">
                        <button class="ai-suggest-btn" onclick="aiSuggestCookTime(event)">AI建议</button>
                    </div>
                </div>
                <div class="form-item">
                    <label class="form-label">目标温度（℃）</label>
                    <div style="display:flex;align-items:center;gap:8px">
                        <input class="form-input" type="number" id="fRecTemp" value="${r ? r.temperature : ''}" placeholder="请输入目标温度" style="flex:1">
                        <button class="ai-suggest-btn" onclick="aiSuggestTemperature(event)">AI建议</button>
                    </div>
                </div>
                <div class="form-item span-2">
                    <label class="form-label">菜谱做法 / 烹饪步骤</label>
                    <textarea class="form-textarea" id="fRecMethod" placeholder="请输入菜谱做法，多个步骤用换行分隔">${r ? (r.method || '') : ''}</textarea>
                </div>
            </div>

            <!-- 食材清单（关联物料表） -->
            <div class="dynamic-table" style="margin-top:16px">
                <label class="form-label required" style="margin-bottom:8px;display:block">所需食材</label>
                <table id="ingredientTable" style="width:100%;border-collapse:collapse;font-size:13px">
                    <thead style="background:#f5f7fa">
                        <tr>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">物料名称</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">规格</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">数量</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">单位</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">是否主料</th>
                            <th style="padding:8px;text-align:left;border-bottom:1px solid #ebeef5">操作</th>
                        </tr>
                    </thead>
                    <tbody id="ingredientTableBody">${defaultIngRows}</tbody>
                </table>
                <button class="btn btn-default add-row-btn" onclick="addIngredientRow()">+ 添加食材</button>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveRecipe()">保存</button>
        </div>
    `, '800px');
}

/* 物料-规格-单位 Mock 映射表 */
const MAT_SPEC_MAP = {
    '猪里脊': [{ spec: '里脊肉/kg', unit: 'kg' }, { spec: '五花肉/kg', unit: 'kg' }, { spec: '前腿肉/kg', unit: 'kg' }],
    '草鱼':   [{ spec: '整条/条', unit: '条' }, { spec: '鱼片/kg', unit: 'kg' }],
    '西兰花': [{ spec: '500g/袋', unit: 'g' }, { spec: '1kg/袋', unit: 'g' }],
    '胡萝卜': [{ spec: '500g/袋', unit: 'g' }, { spec: '1kg/袋', unit: 'g' }],
    '大米':   [{ spec: '500g/袋', unit: 'g' }, { spec: '10kg/袋', unit: 'kg' }, { spec: '25kg/袋', unit: 'kg' }],
    '菜籽油': [{ spec: '5L/桶', unit: 'L' }, { spec: '1L/瓶', unit: 'L' }],
    '食盐':   [{ spec: '500g/袋', unit: 'g' }, { spec: '1kg/袋', unit: 'g' }],
    '鸡腿':   [{ spec: '1kg/袋', unit: 'kg' }, { spec: '500g/袋', unit: 'g' }],
};

/* 构建单行食材行 HTML（新增传 null，编辑传 ing 对象） */
function _buildIngredientRow(ing) {
    const materials = window.mockData.materials || [];
    const matOptions = materials.map(m =>
        `<option value="${m.id}" data-name="${m.name}" data-unit="${m.unit}" ${ing && ing.materialId === m.id ? 'selected' : ''}>${m.name}</option>`
    ).join('');

    // 编辑时根据已选物料构建规格选项
    let specOptions = '<option value="">请选择规格</option>';
    if (ing && ing.materialName && MAT_SPEC_MAP[ing.materialName]) {
        specOptions += MAT_SPEC_MAP[ing.materialName].map(s =>
            `<option value="${s.spec}" data-unit="${s.unit}" ${ing.spec === s.spec ? 'selected' : ''}>${s.spec}</option>`
        ).join('');
    } else if (ing && ing.spec) {
        specOptions += `<option value="${ing.spec}" selected>${ing.spec}</option>`;
    }

    const unit = ing ? (ing.unit || '') : '';

    return `<tr>
        <td style="padding:4px">
            <select class="form-select ing-material" style="width:100%" onchange="onIngMaterialChange(this)">
                <option value="">请选择物料</option>
                ${matOptions}
            </select>
        </td>
        <td style="padding:4px">
            <select class="form-select ing-spec" style="width:100%" onchange="onIngSpecChange(this)">
                ${specOptions}
            </select>
        </td>
        <td style="padding:4px"><input class="form-input ing-qty" type="number" style="width:80px" value="${ing ? ing.quantity : ''}" placeholder="数量"></td>
        <td style="padding:4px"><input class="form-input ing-unit" style="width:70px;background:#f5f7fa;cursor:not-allowed" value="${unit}" placeholder="单位" readonly></td>
        <td style="padding:4px;text-align:center">
            <input type="checkbox" class="ing-main" ${ing && ing.isMain ? 'checked' : ''}>
        </td>
        <td style="padding:4px">
            <button class="btn-link danger" onclick="removeIngredientRow(this)">删除</button>
        </td>
    </tr>`;
}

/* 选择��料后动��更新规格下拉，清空单位 */
function onIngMaterialChange(sel) {
    const opt     = sel.options[sel.selectedIndex];
    const matName = opt.text;
    const row     = sel.closest('tr');
    const specSel = row.querySelector('.ing-spec');
    const unitInp = row.querySelector('.ing-unit');

    // 重建规格选项
    const specs = MAT_SPEC_MAP[matName] || [];
    specSel.innerHTML = '<option value="">请选择规格</option>' +
        specs.map(s => `<option value="${s.spec}" data-unit="${s.unit}">${s.spec}</option>`).join('');

    // 若无映射，用物料默认规格兜底
    if (!specs.length && opt.dataset) {
        const defaultSpec = window.mockData.materials.find(m => m.name === matName);
        if (defaultSpec) {
            specSel.innerHTML += `<option value="${defaultSpec.spec}" data-unit="${defaultSpec.unit}">${defaultSpec.spec}</option>`;
        }
    }
    unitInp.value = '';
}

/* 选择规格后自动填充单位 */
function onIngSpecChange(sel) {
    const opt = sel.options[sel.selectedIndex];
    const row = sel.closest('tr');
    const unitInp = row.querySelector('.ing-unit');
    unitInp.value = opt.dataset.unit || '';
}

/* ============================================================
   动态增删食材行
   ============================================================ */
function addIngredientRow() {
    document.getElementById('ingredientTableBody').insertAdjacentHTML('beforeend', _buildIngredientRow(null));
}

function removeIngredientRow(btn) {
    const tbody = document.getElementById('ingredientTableBody');
    if (tbody.children.length <= 1) {
        showToast('至少保留一行食材', 'error');
        return;
    }
    btn.closest('tr').remove();
}

/* ============================================================
   AI 建议按钮（烹饪时长 + 目标温度）
   逻辑：读取菜谱类别、做法、已选食材，结合食品安全标准 Mock 规则推荐
   ============================================================ */
function aiSuggestCookTime(event) {
    const category = document.getElementById('fRecCategory').value;
    const method   = (document.getElementById('fRecMethod').value || '').toLowerCase();
    const matNames = _getSelectedMatNames();

    const baseMap = { '荤菜': 40, '素菜': 8, '汤类': 20, '主食': 30, '点心': 25 };
    let suggestion = baseMap[category] || 30;

    const hasMeat = matNames.some(n => ['猪里脊','鸡腿','草鱼'].includes(n));
    if (hasMeat && category === '荤菜') suggestion = Math.max(suggestion, 45);
    if (/炖|焖|煨/.test(method)) suggestion = Math.max(suggestion, 60);
    if (/快炒|爆炒/.test(method)) suggestion = Math.min(suggestion, 10);

    document.getElementById('fRecCookTime').value = suggestion;
    const matTip = matNames.length ? `（含食材：${matNames.slice(0,3).join('、')}）` : '';
    showAiTooltip(`已为您推荐符合食品安全标准的烹饪时长：${suggestion} 分钟${matTip}`, event);
}

function aiSuggestTemperature(event) {
    const category = document.getElementById('fRecCategory').value;
    const matNames = _getSelectedMatNames();

    const hasMeat    = matNames.some(n => ['猪里脊','鸡腿'].includes(n));
    const hasSeafood = matNames.some(n => ['草鱼'].includes(n));

    let suggestion = 100;
    let reason = '';

    if (hasMeat || category === '荤菜') {
        suggestion = 95;
        reason = '肉类食品安全标准要求烹饪温度≥70℃，推荐设置95℃确保充分熟透';
    } else if (hasSeafood) {
        suggestion = 85;
        reason = '水产品食品安全标准要求烹饪温度≥63℃，推荐设置85℃';
    } else if (category === '素菜') {
        suggestion = 180;
        reason = '素菜大火翻炒，锅温建议180℃以上，保留营养同时确保熟透';
    } else if (category === '汤类') {
        suggestion = 100;
        reason = '汤类需沸腾（100℃）后转小火，确保食材充分熟透';
    } else if (category === '点心') {
        suggestion = 170;
        reason = '点心烘烤/蒸制建议170℃，确保内部熟透';
    } else {
        suggestion = 100;
        reason = '主食蒸煮建议100℃，确保充分熟透';
    }

    document.getElementById('fRecTemp').value = suggestion;
    showAiTooltip(`已为您推荐符合食品安全标准的烹饪温度：${suggestion}℃\n${reason}`, event);
}

/* 获取当前食材行已选物料名称列表 */
function _getSelectedMatNames() {
    const sels = document.querySelectorAll('#ingredientTableBody .ing-material');
    const names = [];
    sels.forEach(sel => {
        if (sel.selectedIndex > 0) names.push(sel.options[sel.selectedIndex].text);
    });
    return names;
}

/* ============================================================
   保存菜谱（新增 / 编辑）
   ============================================================ */
function saveRecipe() {
    const name = (document.getElementById('fRecName').value || '').trim();
    if (!name) { showToast('请填写菜谱名称', 'error'); return; }

    // 收集食材行
    const rows = document.querySelectorAll('#ingredientTableBody tr');
    const ingredients = [];
    let ingValid = true;
    rows.forEach(row => {
        const sel      = row.querySelector('.ing-material');
        const matId    = sel ? parseInt(sel.value) : 0;
        const matName  = sel && sel.selectedIndex > 0 ? sel.options[sel.selectedIndex].text : '';
        const spec     = row.querySelector('.ing-spec').value.trim();
        const qty      = parseFloat(row.querySelector('.ing-qty').value);
        const unit     = row.querySelector('.ing-unit').value.trim();
        const isMain   = row.querySelector('.ing-main').checked;
        if (!matName) { ingValid = false; return; }
        ingredients.push({ materialId: matId, materialName: matName, spec, quantity: qty || 0, unit, isMain });
    });
    if (!ingValid) { showToast('请为每行食材选择物料', 'error'); return; }
    if (ingredients.length === 0) { showToast('请至少添加一种食材', 'error'); return; }

    const cookTime    = parseInt(document.getElementById('fRecCookTime').value) || 30;
    const temperature = parseInt(document.getElementById('fRecTemp').value)     || 100;
    const method      = document.getElementById('fRecMethod').value.trim();
    const cookingSteps = method ? method.split('\n').filter(s => s.trim()) : [];

    const fields = {
        name,
        category:     document.getElementById('fRecCategory').value,
        cookTime,
        temperature,
        method,
        cookingSteps,
        ingredients,
        updateTime:   new Date().toISOString().slice(0, 10)
    };

    if (window.editingRecipeId) {
        const idx = window.mockData.recipes.findIndex(r => r.id === window.editingRecipeId);
        if (idx !== -1) {
            window.mockData.recipes[idx] = Object.assign({}, window.mockData.recipes[idx], fields);
        }
    } else {
        window.mockData.recipes.unshift(Object.assign({
            id:             Date.now(),
            calories:       200,
            protein:        10,
            carbs:          20,
            fat:            8,
            sodium:         null,
            fiber:          null,
            nutritionScore: 80,
            isNew:          true
        }, fields));
    }

    window.recFilteredList = null;
    window.recPage = 1;
    closeModal();
    _renderRecipe(document.getElementById('mainContent'));
    showToast(window.editingRecipeId ? '编辑成功' : '新增成功');
}
