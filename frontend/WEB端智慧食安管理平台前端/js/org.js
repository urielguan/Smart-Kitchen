/**
 * org.js — 组织管理模块
 *
 * 运行验证说明：
 *  1. 双击 index.html，点击左侧「系统管理」→「组织管理」进入
 *  2. 验证【多级树形折叠面板】：
 *     - 页面以组织树形式展示，顶级组织（集团/分公司）默认展开，子组织行有缩进体现层级
 *     - 点击组织行左侧箭头「▼/▶」可展开/收起该组织的子组织
 *  3. 验证【搜索过滤】：在搜索框输入"食堂"，树节点中只保留名称包含"食堂"的组织（含其父级路径）
 *  4. 验证【新增子组织】：点击蓝色「＋」图标按钮，弹出新增弹窗且父级组织已预选
 *  5. 验证【查看成员】：点击绿色「👥」图标按钮，弹出该组织成员列表
 *  6. 验证【编辑/删除】：黄色编辑、红色删除按钮功能与原有一致
 *  7. 验证【导入/导出】：点击按钮弹出 Toast 提示（Mock 交互）
 *
 * 核心改动点：
 *  - renderOrgPage / _renderOrg / _renderOrgRows 重写为树形折叠面板布局
 *  - 新增 _buildOrgTree / _renderOrgTreeNode 递归渲染多级树
 *  - 新增 _orgToggle 展开/收起节点
 *  - 新增 orgViewMembers 查看关联成员
 *  - 顶部操作栏新增「导入」「导出」「筛选」按钮（Mock 交互）
 *  - 弹窗/保存/删除逻辑完全不变
 */

/* ============================================================
   状态
   ============================================================ */
window.orgExpandedMap  = window.orgExpandedMap  || {};   // orgId -> boolean
window.orgSearchKw     = window.orgSearchKw     || '';
window.orgFilteredList = null;   // 保留兼容旧引用

/* 组织类型映射 */
const ORG_TYPE_MAP = { group: '集团', company: '分公司', canteen: '食堂', dept: '部门' };
const ORG_TYPE_COLOR = {
    group:   { bg:'#ecf5ff', color:'#409eff' },
    company: { bg:'#f0f9eb', color:'#67c23a' },
    canteen: { bg:'#fdf6ec', color:'#e6a23c' },
    dept:    { bg:'#f5f5f5', color:'#909399' }
};

/* ============================================================
   主入口
   ============================================================ */
function renderOrgPage(container) {
    window.orgSearchKw = '';
    window.orgExpandedMap = {};   // 重置展开状态（默认全展开由渲染时判断）
    _renderOrg(container);
}

/* ============================================================
   树形面板渲染
   ============================================================ */
function _renderOrg(container) {
    const all    = window.mockData.orgs;
    const total  = all.length;
    const active = all.filter(o => o.status === 'active').length;

    container.innerHTML = `
        <div class="page-card">
            <!-- 统计条 -->
            <div class="stats-cards" style="margin-bottom:16px;">
                <div class="stat-card">
                    <div class="stat-card-title">组织总数</div>
                    <div class="stat-card-value">${total}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-card-title">启用中</div>
                    <div class="stat-card-value" style="color:#67c23a">${active}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-card-title">已停用</div>
                    <div class="stat-card-value" style="color:#f56c6c">${total - active}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-card-title">组织类型数</div>
                    <div class="stat-card-value" style="color:#409eff">${[...new Set(all.map(o => o.orgType))].length}</div>
                </div>
            </div>

            <!-- 顶部操作栏 -->
            <div style="display:flex;align-items:center;gap:10px;margin-bottom:16px;flex-wrap:wrap;">
                <!-- 左侧：搜索 + 筛选 -->
                <div style="position:relative;flex:1;min-width:220px;max-width:320px;">
                    <span style="position:absolute;left:10px;top:50%;transform:translateY(-50%);color:#909399;font-size:14px;">🔍</span>
                    <input id="orgSearchInput" class="form-control"
                           style="padding-left:32px;"
                           placeholder="搜索组织名称或编码"
                           value="${window.orgSearchKw}"
                           oninput="_orgOnSearch(this.value)">
                </div>
                <button class="btn btn-default" style="display:flex;align-items:center;gap:4px;"
                        onclick="_orgOnSearch(document.getElementById('orgSearchInput').value)">
                    <span>▼</span> 筛选
                </button>
                <!-- 右侧：导入/导出/新增 -->
                <div style="margin-left:auto;display:flex;gap:8px;">
                    <button class="btn btn-default" style="display:flex;align-items:center;gap:4px;"
                            onclick="showToast('导入功能开发中，敬请期待')">
                        ⬆ 导入
                    </button>
                    <button class="btn btn-default" style="display:flex;align-items:center;gap:4px;"
                            onclick="showToast('导出功能开发中，敬请期待')">
                        ⬇ 导出
                    </button>
                    <button class="btn btn-primary" style="display:flex;align-items:center;gap:4px;"
                            onclick="openOrgModal()">
                        ＋ 新增组织
                    </button>
                </div>
            </div>

            <!-- 树形列表 -->
            <div id="orgTreeContainer"
                 style="border:1px solid #e4e7ed;border-radius:6px;overflow:hidden;background:#fff;">
            </div>
        </div>`;

    _renderOrgTree();
}

/* ============================================================
   构建并渲染组织树
   ============================================================ */
function _renderOrgTree() {
    const container = document.getElementById('orgTreeContainer');
    if (!container) return;

    const kw   = (window.orgSearchKw || '').trim().toLowerCase();
    const all  = window.mockData.orgs;

    /* 关键字过滤：保留匹配节点及其所有祖先 */
    let visibleIds = null;
    if (kw) {
        const matchIds = new Set(all.filter(o =>
            o.orgName.toLowerCase().includes(kw) || o.orgCode.toLowerCase().includes(kw)
        ).map(o => o.id));

        /* 补充祖先节点 */
        const withAncestors = new Set(matchIds);
        all.forEach(o => {
            if (matchIds.has(o.id)) {
                let cur = o;
                while (cur.parentId) {
                    withAncestors.add(cur.parentId);
                    cur = all.find(x => x.id === cur.parentId) || { parentId: 0 };
                }
            }
        });
        visibleIds = withAncestors;
    }

    /* 取顶级节点 */
    const roots = all.filter(o => !o.parentId || o.parentId === 0);

    if (roots.length === 0) {
        container.innerHTML = `<div style="text-align:center;color:#909399;padding:40px;">
            暂无组织数据，点击「新增组织」创建</div>`;
        return;
    }

    let html = '';
    roots.forEach(root => {
        if (visibleIds && !visibleIds.has(root.id)) return;
        html += _renderOrgTreeNode(root, 0, all, visibleIds, kw);
    });

    if (!html) {
        html = `<div style="text-align:center;color:#909399;padding:40px;">
            未找到与「${kw}」匹配的组织</div>`;
    }

    container.innerHTML = html;
}

/* 递归渲染单个节点 */
function _renderOrgTreeNode(org, depth, all, visibleIds, kw) {
    const children = all.filter(o => o.parentId === org.id)
                        .filter(o => !visibleIds || visibleIds.has(o.id));
    const hasChild  = children.length > 0;

    /* 展开状态：默认展开前两级 */
    if (window.orgExpandedMap[org.id] === undefined) {
        window.orgExpandedMap[org.id] = depth < 2;
    }
    const expanded = window.orgExpandedMap[org.id];

    /* 父级组织名称（只在子节点显示） */
    const parentOrg = org.parentId ? all.find(x => x.id === org.parentId) : null;

    /* 组织类型样式 */
    const tc  = ORG_TYPE_COLOR[org.orgType] || { bg:'#f5f5f5', color:'#606266' };
    const tl  = ORG_TYPE_MAP[org.orgType]   || org.orgType;

    /* 成员数（从 employees 读取，若无该字段给 Mock 值） */
    const memberCount = (window.mockData.employees || []).filter(e => e.orgId === org.id).length;

    /* 状态标签 */
    const stCls = org.status === 'active' ? 'tag-success' : 'tag-danger';
    const stLbl = org.status === 'active' ? '启用' : '停用';

    /* 左侧缩进 */
    const indent = depth * 24;

    /* 箭头 */
    const arrow = hasChild
        ? `<span onclick="event.stopPropagation();_orgToggle(${org.id})"
                  style="display:inline-flex;align-items:center;justify-content:center;
                         width:20px;height:20px;cursor:pointer;color:#409eff;font-size:12px;
                         flex-shrink:0;transition:transform 0.2s;
                         transform:rotate(${expanded ? '0' : '-90'}deg);">▼</span>`
        : `<span style="display:inline-block;width:20px;flex-shrink:0;"></span>`;

    /* 高亮关键字 */
    const dispName = kw
        ? org.orgName.replace(new RegExp(`(${kw})`, 'gi'), '<mark style="background:#fff3cc;padding:0;">$1</mark>')
        : org.orgName;

    let html = `
        <div style="display:flex;align-items:flex-start;padding:12px 16px;
                    border-bottom:1px solid #f0f0f0;
                    padding-left:${16 + indent}px;
                    background:#fff;transition:background 0.15s;"
             onmouseover="this.style.background='#fafafa'"
             onmouseout="this.style.background='#fff'">

            <!-- 左：箭头 + 图标 + 主信息 -->
            <div style="display:flex;align-items:flex-start;flex:1;min-width:0;gap:6px;">
                ${arrow}
                <span style="font-size:16px;flex-shrink:0;margin-top:1px;">🏢</span>
                <div style="min-width:0;flex:1;">
                    <!-- 名称行 -->
                    <div style="display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-bottom:4px;">
                        <span style="font-weight:600;font-size:14px;color:#303133;">${dispName}</span>
                        <span style="font-size:12px;padding:1px 8px;border-radius:10px;
                                     background:${tc.bg};color:${tc.color};flex-shrink:0;">${tl}</span>
                    </div>
                    <!-- 副信息行 -->
                    <div style="font-size:12px;color:#909399;display:flex;flex-wrap:wrap;gap:12px;">
                        <span>编码：<code style="background:#f5f7fa;padding:1px 5px;border-radius:3px;color:#606266;">${org.orgCode}</code></span>
                        ${parentOrg ? `<span>父级：${parentOrg.orgName}</span>` : ''}
                        <span>👤 成员：${memberCount} 人</span>
                        ${org.leaderName ? `<span>负责人：${org.leaderName}</span>` : ''}
                    </div>
                </div>
            </div>

            <!-- 右：状态 + 操作 -->
            <div style="display:flex;align-items:center;gap:8px;flex-shrink:0;margin-left:12px;">
                <span class="tag ${stCls}" style="font-size:11px;border-radius:10px;">${stLbl}</span>
                <!-- 蓝色：新增子组织 -->
                <button title="新增子组织" onclick="openOrgModal();_orgPresetParent(${org.id})"
                        style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                               background:#ecf5ff;color:#409eff;font-size:14px;
                               display:flex;align-items:center;justify-content:center;">＋</button>
                <!-- 绿色：查看成员 -->
                <button title="查看成员" onclick="orgViewMembers(${org.id})"
                        style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                               background:#f0f9eb;color:#67c23a;font-size:14px;
                               display:flex;align-items:center;justify-content:center;">👥</button>
                <!-- 黄色：编辑 -->
                <button title="编辑组织" onclick="openOrgModal(${org.id})"
                        style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                               background:#fdf6ec;color:#e6a23c;font-size:14px;
                               display:flex;align-items:center;justify-content:center;">✏️</button>
                <!-- 红色：删除 -->
                <button title="删除组织" onclick="deleteOrg(${org.id})"
                        style="width:28px;height:28px;border:none;border-radius:4px;cursor:pointer;
                               background:#fef0f0;color:#f56c6c;font-size:14px;
                               display:flex;align-items:center;justify-content:center;">🗑️</button>
            </div>
        </div>`;

    /* 递归子节点（受展开状态控制） */
    if (hasChild) {
        const childrenHtml = children.map(c =>
            _renderOrgTreeNode(c, depth + 1, all, visibleIds, kw)
        ).join('');

        html += `<div id="orgChildren_${org.id}" style="display:${expanded ? 'block' : 'none'};">
                    ${childrenHtml}
                 </div>`;
    }

    return html;
}

/* ============================================================
   展开 / 收起
   ============================================================ */
function _orgToggle(orgId) {
    window.orgExpandedMap[orgId] = !window.orgExpandedMap[orgId];
    _renderOrgTree();
}

/* ============================================================
   搜索
   ============================================================ */
function _orgOnSearch(val) {
    window.orgSearchKw = val;
    _renderOrgTree();
}

/* ============================================================
   新增子组织预设父级（弹窗打开后自动选中父级）
   ============================================================ */
function _orgPresetParent(parentId) {
    const sel = document.getElementById('fOrgParent');
    if (sel) sel.value = String(parentId);
}

/* ============================================================
   查看关联成员弹窗
   ============================================================ */
function orgViewMembers(orgId) {
    const org     = window.mockData.orgs.find(o => o.id === orgId);
    const members = (window.mockData.employees || []).filter(e => e.orgId === orgId);

    let rows = members.length
        ? members.map((e, i) => `
            <tr>
                <td>${i + 1}</td>
                <td>${e.empNo}</td>
                <td>${e.name}</td>
                <td>${e.deptName || '-'}</td>
                <td>${e.position ? (window.EMP_POSITION_MAP ? (window.EMP_POSITION_MAP[e.position] || e.position) : e.position) : '-'}</td>
                <td>${e.status === 'active'
                    ? '<span class="tag tag-success">启用</span>'
                    : '<span class="tag tag-info">禁用</span>'}</td>
            </tr>`).join('')
        : '<tr><td colspan="6" style="text-align:center;color:#909399;padding:20px;">该组织暂无成员</td></tr>';

    showModal(`
        <div class="modal-header"><h3>「${org ? org.orgName : ''}」关联成员（${members.length} 人）</h3></div>
        <div class="modal-body" style="max-height:65vh;overflow-y:auto;">
            <table class="data-table">
                <thead><tr><th>序号</th><th>员工编号</th><th>姓名</th><th>部门</th><th>职位</th><th>状态</th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">关 闭</button>
        </div>`, '560px');
}

/* ============================================================
   保留旧函数签名（兼容 saveOrg/deleteOrg 调用的刷新逻辑）
   ============================================================ */
function orgChangePage() { _renderOrgTree(); }
function searchOrgs()    { _orgOnSearch(document.getElementById('orgSearchInput')?.value || ''); }
function resetOrgSearch() {
    window.orgSearchKw = '';
    const inp = document.getElementById('orgSearchInput');
    if (inp) inp.value = '';
    _renderOrgTree();
}

function deleteOrg(id) {
    const org = window.mockData.orgs.find(o => o.id === id);
    if (!org) return;
    const hasChildren = window.mockData.orgs.some(o => o.parentId === id);
    if (hasChildren) {
        showToast('该组织下存在子组织，无法删除', 'error');
        return;
    }
    if (!confirm('确认删除该组织？')) return;
    window.mockData.orgs = window.mockData.orgs.filter(o => o.id !== id);
    if (window.orgFilteredList !== null) {
        window.orgFilteredList = window.orgFilteredList.filter(o => o.id !== id);
    }
    _renderOrgTree();
    showToast('删除成功');
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showOrgDetail(id) {
    const o = window.mockData.orgs.find(o => o.id === id);
    if (!o) return;

    const typeMap   = { group: '集团', company: '分公司', canteen: '食堂', dept: '部门' };
    const parentOrg = o.parentId
        ? (window.mockData.orgs.find(x => x.id === o.parentId) || {}).orgName || '—'
        : '—（顶级组织）';
    const children  = window.mockData.orgs.filter(x => x.parentId === o.id);
    const childrenHtml = children.length
        ? children.map(c => `<span class="tag tag-info" style="margin:2px">${c.orgName}</span>`).join('')
        : '<span style="color:#909399">无子组织</span>';

    showModal(`
        <div class="modal-header">
            <span class="modal-title">🏗️ 组织详情</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="detail-section">
                <div class="detail-section-title">基本信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">组织名称</span><span class="detail-value">${o.orgName}</span></div>
                    <div class="detail-item"><span class="detail-label">组织编码</span><span class="detail-value">${o.orgCode}</span></div>
                    <div class="detail-item"><span class="detail-label">组织类型</span><span class="detail-value">${typeMap[o.orgType] || o.orgType}</span></div>
                    <div class="detail-item"><span class="detail-label">组织层级</span><span class="detail-value">第 ${o.level} 级</span></div>
                    <div class="detail-item"><span class="detail-label">上级组织</span><span class="detail-value">${parentOrg}</span></div>
                    <div class="detail-item"><span class="detail-label">组织路径</span><span class="detail-value">${o.path || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">状态</span><span class="detail-value">${o.status === 'active' ? '<span class="tag tag-success">启用</span>' : '<span class="tag tag-danger">停用</span>'}</span></div>
                    <div class="detail-item"><span class="detail-label">排序序号</span><span class="detail-value">${o.sortOrder ?? 0}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">联系信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">负责人</span><span class="detail-value">${o.leaderName || '—'}</span></div>
                    <div class="detail-item"><span class="detail-label">联系电话</span><span class="detail-value">${o.contactPhone || '—'}</span></div>
                    <div class="detail-item" style="grid-column:span 2"><span class="detail-label">地址</span><span class="detail-value">${o.address || '—'}</span></div>
                </div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">子组织</div>
                <div style="padding:4px 0">${childrenHtml}</div>
            </div>
            <div class="detail-section">
                <div class="detail-section-title">时间信息</div>
                <div class="detail-grid">
                    <div class="detail-item"><span class="detail-label">创建时间</span><span class="detail-value">${o.createdAt}</span></div>
                    <div class="detail-item"><span class="detail-label">更新时间</span><span class="detail-value">${o.updatedAt || '—'}</span></div>
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-primary" onclick="openOrgModal(${o.id});closeModal()">编辑</button>
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '640px');
}

/* ============================================================
   新增 / 编辑弹窗
   ============================================================ */
function openOrgModal(id) {
    window.editingOrgId = id || null;
    const o     = id ? window.mockData.orgs.find(x => x.id === id) : null;
    const title = o ? '编辑组织' : '新增组织';

    // 构建父级组织下拉（排除自身及其子孙）
    const excludeIds = new Set();
    if (id) {
        excludeIds.add(id);
        window.mockData.orgs.forEach(x => { if (x.parentId === id) excludeIds.add(x.id); });
    }
    const parentOptions = window.mockData.orgs
        .filter(x => !excludeIds.has(x.id))
        .map(x => `<option value="${x.id}" ${o && o.parentId === x.id ? 'selected' : ''}>${x.orgName}（${x.orgCode}）</option>`)
        .join('');

    showModal(`
        <div class="modal-header">
            <span class="modal-title">${title}</span>
            <span class="modal-close" onclick="closeModal()">✕</span>
        </div>
        <div class="modal-body">
            <div class="form-grid">
                <div class="form-item">
                    <label class="form-label required">组织名称</label>
                    <input class="form-input" id="fOrgName" value="${o ? o.orgName : ''}" placeholder="请输入组织名称">
                </div>
                <div class="form-item">
                    <label class="form-label required">组织编码</label>
                    <input class="form-input" id="fOrgCode" value="${o ? o.orgCode : ''}" placeholder="如：ORG-001" ${o ? 'readonly' : ''}>
                </div>
                <div class="form-item">
                    <label class="form-label required">组织类型</label>
                    <select class="form-select" id="fOrgType">
                        <option value="group"   ${o && o.orgType==='group'   ? 'selected' : ''}>集团</option>
                        <option value="company" ${o && o.orgType==='company' ? 'selected' : ''}>分公司</option>
                        <option value="canteen" ${o && o.orgType==='canteen' ? 'selected' : ''}>食堂</option>
                        <option value="dept"    ${o && o.orgType==='dept'    ? 'selected' : ''}>部门</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">上级组织</label>
                    <select class="form-select" id="fOrgParent">
                        <option value="">— 顶级组织（无上级）</option>
                        ${parentOptions}
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">负责人</label>
                    <input class="form-input" id="fOrgLeader" value="${o ? (o.leaderName || '') : ''}" placeholder="请输入负责人姓名">
                </div>
                <div class="form-item">
                    <label class="form-label">联系电话</label>
                    <input class="form-input" id="fOrgPhone" value="${o ? (o.contactPhone || '') : ''}" placeholder="请输入联系电话">
                </div>
                <div class="form-item">
                    <label class="form-label">状态</label>
                    <select class="form-select" id="fOrgStatus">
                        <option value="active"   ${!o || o.status==='active'   ? 'selected' : ''}>启用</option>
                        <option value="inactive" ${o && o.status==='inactive'  ? 'selected' : ''}>停用</option>
                    </select>
                </div>
                <div class="form-item">
                    <label class="form-label">排序序号</label>
                    <input class="form-input" type="number" id="fOrgSort" value="${o ? (o.sortOrder ?? 0) : 0}" min="0">
                </div>
                <div class="form-item span-2">
                    <label class="form-label">地址</label>
                    <input class="form-input" id="fOrgAddress" value="${o ? (o.address || '') : ''}" placeholder="请输入组织地址">
                </div>
            </div>
        </div>
        <div class="modal-footer">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="saveOrg()">保存</button>
        </div>
    `, '680px');
}

/* ============================================================
   保存组织（新增 / 编辑）
   ============================================================ */
function saveOrg() {
    const orgName = (document.getElementById('fOrgName').value || '').trim();
    const orgCode = (document.getElementById('fOrgCode').value || '').trim();
    if (!orgName) { showToast('请输入组织名称', 'error'); return; }
    if (!orgCode) { showToast('请输入组织编码', 'error'); return; }

    // 编码唯一性校验（新增时）
    if (!window.editingOrgId) {
        const exists = window.mockData.orgs.some(o => o.orgCode === orgCode);
        if (exists) { showToast('组织编码已存在，请更换', 'error'); return; }
    }

    const parentId  = parseInt(document.getElementById('fOrgParent').value) || 0;
    const parentOrg = parentId ? window.mockData.orgs.find(o => o.id === parentId) : null;
    const level     = parentOrg ? parentOrg.level + 1 : 1;
    const path      = parentOrg ? parentOrg.path + (parentOrg.id) + '/' : '/';

    const fields = {
        orgName,
        orgCode,
        orgType:      document.getElementById('fOrgType').value,
        parentId:     parentId || 0,
        level,
        path,
        leaderName:   document.getElementById('fOrgLeader').value.trim(),
        contactPhone: document.getElementById('fOrgPhone').value.trim(),
        address:      document.getElementById('fOrgAddress').value.trim(),
        status:       document.getElementById('fOrgStatus').value,
        sortOrder:    parseInt(document.getElementById('fOrgSort').value) || 0,
        updatedAt:    new Date().toLocaleString('zh-CN')
    };

    if (window.editingOrgId) {
        const idx = window.mockData.orgs.findIndex(o => o.id === window.editingOrgId);
        if (idx !== -1) {
            window.mockData.orgs[idx] = Object.assign({}, window.mockData.orgs[idx], fields);
        }
    } else {
        window.mockData.orgs.push(Object.assign({
            id:        Date.now(),
            tenantId:  1,
            createdAt: new Date().toLocaleString('zh-CN')
        }, fields));
    }

    window.orgFilteredList = null;
    window.orgPage = 1;
    closeModal();
    _renderOrgTree();
    showToast(window.editingOrgId ? '编辑成功' : '新增成功');
}
