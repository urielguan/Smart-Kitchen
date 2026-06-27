/**
 * review.js — 评价管理模块
 *
 * 运行验证说明：
 * ① 点击左侧「评价管理」导航 → 默认展示「评价与投诉列表」Tab
 * ② 列表：按类型/来源/处理状态/派单方式/时间范围/门店多维筛选，分页，类型标签蓝色(评价)/红色(投诉)
 * ③ 点击「详情」→ 弹窗3个Tab（基础信息/派单信息/处理记录）展示所有字段
 * ④ 点击「自动派单」（未派单的投诉/高优先级）→ 系统按规则匹配处理人并派单，Toast提示，列表实时刷新
 * ⑤ 点击「人工派单」→ 弹窗填写处理人员/截止时间/优先级/备注 → 提交后列表/详情实时同步
 * ⑥ 切换「派单记录」Tab → 展示所有已派单记录（自动+人工），含派单人/处理人/截止时间/状态
 *
 * 核心改动点：
 * - 新增评价管理导航入口（sidebar.js + index.html）
 * - 新增 mockData.evaComplaints 数据（mock-data.js）
 * - 新增评价与投诉列表整合展示、类型/状态标签视觉区分
 * - 新增自动派单Mock规则（按投诉类型匹配处理人）
 * - 新增人工派单弹窗（必填校验、提交同步更新）
 * - 新增详情弹窗3-Tab展示（基础信息/派单信息/处理记录）
 * - 新增派单记录Tab独立展示
 */

/* ============================================================
   常量
   ============================================================ */
const EV_DOC_TYPE_MAP = {
    review:    { label: '评价', cls: 'tag-primary' },
    complaint: { label: '投诉', cls: 'tag-danger'  }
};

const EV_SOURCE_MAP = {
    meal:       '用餐评价',
    supervision:'监管反馈',
    manual:     '人工录入'
};

const EV_STATUS_MAP = {
    pending:   { label: '未处理', cls: 'tag-info'    },
    assigned:  { label: '已派单', cls: 'tag-warning' },
    handling:  { label: '处理中', cls: 'tag-primary' },
    closed:    { label: '已闭环', cls: 'tag-success' }
};

const EV_ASSIGN_MAP = {
    none:   { label: '未派单', cls: 'tag-info'    },
    auto:   { label: '自动派单', cls: 'tag-primary' },
    manual: { label: '人工派单', cls: 'tag-warning' }
};

const EV_PRIORITY_MAP = {
    low:    { label: '低', color: '#67c23a' },
    medium: { label: '中', color: '#e6a23c' },
    high:   { label: '高', color: '#f56c6c' }
};

/* 自动派单规则：来源keyword → 处理人(从员工中匹配) */
const AUTO_ASSIGN_RULES = [
    { keywords: ['食品安全','食安','不新鲜','变质'], handlerPosition: 'chef',       note: '食安问题' },
    { keywords: ['菜品','口味','份量','烹饪','火候'], handlerPosition: 'chef',       note: '菜品质量' },
    { keywords: ['服务','态度','摔','骂'],            handlerPosition: 'manager',    note: '服务问题' },
    { keywords: ['卫生','环境','油污','清洁'],        handlerPosition: 'cookworker', note: '卫生环境' }
];

/* ============================================================
   全局状态
   ============================================================ */
window.evActiveTab  = 'list';
window.evPage       = 1;
window.evPageSize   = 10;
window.evFilter     = { docType: '', source: '', handleStatus: '', assignType: '', startDate: '', endDate: '', orgId: '' };
window.asPage       = 1;
window.asPageSize   = 10;
window._evDetailId  = null;
window._evDetailTab = 'basic';

/* ============================================================
   主入口
   ============================================================ */
function renderReviewPage(container) {
    window.evActiveTab = window.evActiveTab || 'list';
    window.evPage = 1;
    window.asPage = 1;

    container.innerHTML = `
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
            <div style="display:flex;gap:0;border:1px solid #dcdfe6;border-radius:4px;overflow:hidden;">
                <div onclick="evSwitchTab('list')"
                     style="padding:8px 24px;cursor:pointer;font-size:14px;
                            background:${window.evActiveTab==='list'?'#409eff':'#fff'};
                            color:${window.evActiveTab==='list'?'#fff':'#606266'};
                            border:none;transition:all .2s;">
                    📋 评价与投诉列表
                </div>
                <div onclick="evSwitchTab('assign')"
                     style="padding:8px 24px;cursor:pointer;font-size:14px;
                            background:${window.evActiveTab==='assign'?'#409eff':'#fff'};
                            color:${window.evActiveTab==='assign'?'#fff':'#606266'};
                            border:none;border-left:1px solid #dcdfe6;transition:all .2s;">
                    📨 派单记录
                </div>
            </div>
        </div>
        <div id="evTabContent"></div>`;

    _evRenderTab();
}

function evSwitchTab(tab) {
    window.evActiveTab = tab;
    renderReviewPage(document.getElementById('mainContent'));
}

function _evRenderTab() {
    const el = document.getElementById('evTabContent');
    if (!el) return;
    window.evActiveTab === 'list' ? _renderEvList(el) : _renderAssignTab(el);
}

/* ============================================================
   评价与投诉列表 Tab
   ============================================================ */
function _renderEvList(container) {
    const f = window.evFilter;
    const orgOpts = (window.mockData.orgs || []).filter(o => o.status === 'active')
        .map(o => `<option value="${o.id}" ${f.orgId==o.id?'selected':''}>${o.orgName}</option>`).join('');

    container.innerHTML = `
        <div class="filter-bar" style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px;">
            <select class="filter-input" style="width:110px;" onchange="window.evFilter.docType=this.value">
                <option value="" ${!f.docType?'selected':''}>类型全部</option>
                <option value="review"    ${f.docType==='review'   ?'selected':''}>评价</option>
                <option value="complaint" ${f.docType==='complaint'?'selected':''}>投诉</option>
            </select>
            <select class="filter-input" style="width:120px;" onchange="window.evFilter.source=this.value">
                <option value="" ${!f.source?'selected':''}>来源全部</option>
                <option value="meal"       ${f.source==='meal'      ?'selected':''}>用餐评价</option>
                <option value="supervision"${f.source==='supervision'?'selected':''}>监管反馈</option>
                <option value="manual"     ${f.source==='manual'    ?'selected':''}>人工录入</option>
            </select>
            <select class="filter-input" style="width:120px;" onchange="window.evFilter.handleStatus=this.value">
                <option value="" ${!f.handleStatus?'selected':''}>状态全部</option>
                <option value="pending"  ${f.handleStatus==='pending' ?'selected':''}>未处理</option>
                <option value="assigned" ${f.handleStatus==='assigned'?'selected':''}>已派单</option>
                <option value="handling" ${f.handleStatus==='handling'?'selected':''}>处理中</option>
                <option value="closed"   ${f.handleStatus==='closed'  ?'selected':''}>已闭环</option>
            </select>
            <select class="filter-input" style="width:120px;" onchange="window.evFilter.assignType=this.value">
                <option value="" ${!f.assignType?'selected':''}>派单方式全部</option>
                <option value="none"   ${f.assignType==='none'  ?'selected':''}>未派单</option>
                <option value="auto"   ${f.assignType==='auto'  ?'selected':''}>自动派单</option>
                <option value="manual" ${f.assignType==='manual'?'selected':''}>人工派单</option>
            </select>
            <select class="filter-input" style="width:130px;" onchange="window.evFilter.orgId=this.value">
                <option value="" ${!f.orgId?'selected':''}>门店全部</option>
                ${orgOpts}
            </select>
            <input type="date" class="filter-input" value="${f.startDate}" title="开始日期"
                   oninput="window.evFilter.startDate=this.value" style="width:145px;">
            <span style="color:#606266;font-size:13px;">至</span>
            <input type="date" class="filter-input" value="${f.endDate}" title="结束日期"
                   oninput="window.evFilter.endDate=this.value" style="width:145px;">
            <button class="btn btn-primary" onclick="evSearch()">🔍 查询</button>
            <button class="btn btn-default" onclick="evReset()">↺ 重置</button>
        </div>
        <div id="evTableWrap"></div>`;

    _renderEvRows();
}

function evSearch() { window.evPage = 1; _renderEvRows(); }
function evReset() {
    window.evFilter = { docType: '', source: '', handleStatus: '', assignType: '', startDate: '', endDate: '', orgId: '' };
    window.evPage = 1;
    _renderEvList(document.getElementById('evTabContent'));
}

function _filterEv() {
    const f = window.evFilter;
    return (window.mockData.evaComplaints || []).filter(d => {
        if (f.docType      && d.docType      !== f.docType)      return false;
        if (f.source       && d.source       !== f.source)       return false;
        if (f.handleStatus && d.handleStatus !== f.handleStatus) return false;
        if (f.assignType   && d.assignType   !== f.assignType)   return false;
        if (f.orgId        && String(d.orgId) !== String(f.orgId)) return false;
        if (f.startDate    && d.createdAt.slice(0,10) < f.startDate) return false;
        if (f.endDate      && d.createdAt.slice(0,10) > f.endDate)   return false;
        return true;
    });
}

function _renderEvRows() {
    const wrap = document.getElementById('evTableWrap');
    if (!wrap) return;

    const list     = _filterEv();
    const total    = list.length;
    const ps       = window.evPageSize;
    const page     = window.evPage;
    const pages    = Math.max(1, Math.ceil(total / ps));
    const pageData = list.slice((page-1)*ps, page*ps);

    if (total === 0 && !(window.mockData.evaComplaints || []).length) {
        wrap.innerHTML = `<div style="text-align:center;padding:60px;color:#909399;">
            <div style="font-size:36px;margin-bottom:10px;">📋</div>
            <div>暂无评价与投诉数据，可等待用户提交或人工录入</div>
        </div>`;
        return;
    }

    let rows = '';
    if (pageData.length === 0) {
        rows = `<tr><td colspan="10" style="text-align:center;color:#999;padding:32px;">筛选条件下无匹配数据</td></tr>`;
    } else {
        pageData.forEach(d => {
            const dt  = EV_DOC_TYPE_MAP[d.docType]       || { label: d.docType,       cls: 'tag-info' };
            const st  = EV_STATUS_MAP[d.handleStatus]    || { label: d.handleStatus,  cls: 'tag-info' };
            const at  = EV_ASSIGN_MAP[d.assignType]      || { label: d.assignType,    cls: 'tag-info' };
            const pri = EV_PRIORITY_MAP[d.priority]      || { label: d.priority,      color: '#909399' };
            const src = EV_SOURCE_MAP[d.source]          || d.source;
            const canAutoPick  = d.handleStatus === 'pending' && (d.docType === 'complaint' || d.overallScore <= 2);
            const canManualPick = d.handleStatus === 'pending' || d.assignType === 'none';

            rows += `<tr>
                <td style="font-family:monospace;font-size:12px;">${d.docNo}</td>
                <td><span class="tag ${dt.cls}">${dt.label}</span></td>
                <td style="font-size:12px;">${src}</td>
                <td style="max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${d.title}">${d.title}</td>
                <td>${d.contactName}</td>
                <td>${d.orgName}</td>
                <td style="font-size:12px;">${d.createdAt.slice(0,16)}</td>
                <td><span class="tag ${st.cls}">${st.label}</span></td>
                <td><span class="tag ${at.cls}">${at.label}</span></td>
                <td><span style="color:${pri.color};font-weight:${d.priority==='high'?'700':'400'};">${pri.label}</span></td>
                <td>
                    <button class="btn btn-info btn-sm" onclick="showEvDetail(${d.id})">详情</button>
                    ${canAutoPick  ? `<button class="btn btn-primary btn-sm" onclick="evAutoAssign(${d.id})">自动派单</button>` : ''}
                    ${canManualPick? `<button class="btn btn-warning btn-sm" onclick="openManualAssign(${d.id})">人工派单</button>` : ''}
                </td>
            </tr>`;
        });
    }

    wrap.innerHTML = `
        <table class="data-table">
            <thead><tr>
                <th>单据编号</th><th>类型</th><th>来源</th><th>标题/菜品</th>
                <th>投诉人</th><th>所属门店</th><th>创建时间</th>
                <th>处理状态</th><th>派单方式</th><th>优先级</th>
                <th style="width:180px;">操作</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
        <div style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="evPaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="evPaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function evPaginate(p) {
    const pages = Math.max(1, Math.ceil(_filterEv().length / window.evPageSize));
    window.evPage = Math.max(1, Math.min(p, pages));
    _renderEvRows();
}

/* ============================================================
   详情弹窗
   ============================================================ */
function showEvDetail(id) {
    window._evDetailId  = id;
    window._evDetailTab = 'basic';
    _renderEvDetailModal();
}

function evDetailTab(tab) {
    window._evDetailTab = tab;
    _renderEvDetailModal();
}

function _renderEvDetailModal() {
    const d   = (window.mockData.evaComplaints || []).find(x => x.id === window._evDetailId);
    if (!d) return;
    const tab = window._evDetailTab;
    const dt  = EV_DOC_TYPE_MAP[d.docType] || { label: d.docType, cls: 'tag-info' };
    const st  = EV_STATUS_MAP[d.handleStatus] || { label: d.handleStatus, cls: 'tag-info' };
    const pri = EV_PRIORITY_MAP[d.priority]   || { label: d.priority, color: '#909399' };

    const tabStyle = t =>
        `padding:7px 18px;cursor:pointer;font-size:13px;
         border-bottom:2px solid ${tab===t?'#409eff':'transparent'};
         color:${tab===t?'#409eff':'#606266'};
         background:none;border-top:none;border-left:none;border-right:none;white-space:nowrap;`;

    let body = '';
    if (tab === 'basic') {
        const scoreHtml = d.overallScore !== null
            ? `${'⭐'.repeat(d.overallScore)}${'☆'.repeat(5-d.overallScore)} ${d.overallScore}星`
            : '—';
        body = `<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;margin-top:12px;">
            ${_evIR('单据编号', d.docNo)}
            ${_evIR('类型', `<span class="tag ${dt.cls}">${dt.label}</span>`)}
            ${_evIR('来源', EV_SOURCE_MAP[d.source]||d.source)}
            ${_evIR('优先级', `<span style="color:${pri.color};font-weight:600;">${pri.label}</span>`)}
            ${_evIR('标题/菜品', d.title)}
            ${_evIR('评分', scoreHtml)}
            ${_evIR('投诉人', d.contactName)}
            ${_evIR('联系方式', d.contactPhone)}
            ${_evIR('所属门店', d.orgName)}
            ${_evIR('创建时间', d.createdAt)}
        </div>
        <div style="margin-top:12px;">
            <div style="font-size:12px;color:#909399;margin-bottom:4px;">投诉/评价内容</div>
            <div style="background:#f5f7fa;border-radius:4px;padding:10px 12px;font-size:13px;color:#303133;line-height:1.6;">${d.content}</div>
        </div>
        <div style="margin-top:10px;">
            <div style="font-size:12px;color:#909399;margin-bottom:4px;">附件</div>
            <div style="border:1px dashed #dcdfe6;border-radius:4px;padding:10px 14px;font-size:12px;color:#c0c4cc;">
                📎 暂无附件上传（Mock占位）
            </div>
        </div>`;
    } else if (tab === 'assign') {
        const at = EV_ASSIGN_MAP[d.assignType] || { label: '未派单', cls: 'tag-info' };
        if (d.assignType === 'none') {
            body = `<div style="margin-top:16px;text-align:center;color:#909399;padding:24px;">
                <div style="font-size:32px;margin-bottom:8px;">📭</div>
                <div>该单据尚未派单</div>
                <div style="margin-top:12px;display:flex;gap:8px;justify-content:center;">
                    ${(d.handleStatus==='pending'&&(d.docType==='complaint'||d.overallScore<=2))
                        ? `<button class="btn btn-primary" onclick="closeModal();evAutoAssign(${d.id})">自动派单</button>`
                        : ''}
                    <button class="btn btn-warning" onclick="closeModal();openManualAssign(${d.id})">人工派单</button>
                </div>
            </div>`;
        } else {
            body = `<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;margin-top:12px;">
                ${_evIR('派单方式', `<span class="tag ${at.cls}">${at.label}</span>`)}
                ${_evIR('派单人员', d.assignerName||'系统自动')}
                ${_evIR('处理人员', d.handlerName||'—')}
                ${_evIR('派单时间', d.assignTime||'—')}
                ${_evIR('处理截止', d.deadline  ||'—')}
            </div>
            ${d.assignRemark?`<div style="margin-top:12px;">
                <div style="font-size:12px;color:#909399;margin-bottom:4px;">派单备注</div>
                <div style="background:#f5f7fa;border-radius:4px;padding:10px 12px;font-size:13px;">${d.assignRemark}</div>
            </div>`:''}`;
        }
    } else {
        // 处理记录
        const logs = d.logs || [];
        const logRows = logs.map(l => `
            <div style="display:flex;gap:12px;padding:8px 0;border-bottom:1px solid #f5f5f5;font-size:13px;">
                <span style="color:#909399;white-space:nowrap;min-width:130px;">${l.time}</span>
                <span style="color:#303133;">${l.op}</span>
            </div>`).join('');

        const handleHtml = d.handleContent ? `
            <div style="margin-top:12px;">
                <div style="font-size:12px;color:#909399;margin-bottom:4px;">处理内容</div>
                <div style="background:#f5f7fa;border-radius:4px;padding:10px 12px;font-size:13px;">${d.handleContent}</div>
            </div>
            <div style="margin-top:8px;">
                ${_evIR('处理结果', d.handleResult==='resolved'?'<span style="color:#67c23a;font-weight:600;">已解决</span>':d.handleResult||'—')}
                ${d.handleTime?_evIR('处理时间', d.handleTime):''}
            </div>` : '';

        body = `<div style="margin-top:12px;">
            <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;">
                ${_evIR('处理状态', `<span class="tag ${st.cls}">${st.label}</span>`)}
                ${d.closeTime?_evIR('闭环时间', d.closeTime):''}
            </div>
            ${handleHtml}
            <div style="margin-top:12px;">
                <div style="font-size:12px;font-weight:600;color:#909399;margin-bottom:8px;">操作流水</div>
                <div style="border:1px solid #ebeef5;border-radius:4px;padding:0 12px;max-height:200px;overflow-y:auto;">
                    ${logRows || '<div style="color:#c0c4cc;padding:16px;text-align:center;">暂无操作记录</div>'}
                </div>
            </div>
        </div>`;
    }

    showModal(`
        <div class="modal-title">【${d.docNo}】${d.title} 详情</div>
        <div style="display:flex;border-bottom:1px solid #ebeef5;margin-bottom:4px;overflow-x:auto;">
            <button style="${tabStyle('basic')}"  onclick="evDetailTab('basic')">基础信息</button>
            <button style="${tabStyle('assign')}" onclick="evDetailTab('assign')">派单信息</button>
            <button style="${tabStyle('handle')}" onclick="evDetailTab('handle')">处理记录</button>
        </div>
        ${body}
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
            ${(d.handleStatus==='pending'&&(d.docType==='complaint'||d.overallScore<=2))
                ? `<button class="btn btn-primary" onclick="closeModal();evAutoAssign(${d.id})">自动派单</button>`
                : ''}
            ${(d.handleStatus==='pending'||d.assignType==='none')
                ? `<button class="btn btn-warning" onclick="closeModal();openManualAssign(${d.id})">人工派单</button>`
                : ''}
            <button class="btn btn-default" onclick="closeModal()">关闭</button>
        </div>
    `, '580px');
}

/* ============================================================
   自动派单
   ============================================================ */
function evAutoAssign(id) {
    const d = (window.mockData.evaComplaints || []).find(x => x.id === id);
    if (!d) return;

    // 按内容关键词匹配规则
    const content  = (d.content || '') + (d.title || '');
    let matchedRule = null;
    for (const rule of AUTO_ASSIGN_RULES) {
        if (rule.keywords.some(kw => content.includes(kw))) {
            matchedRule = rule;
            break;
        }
    }
    // 默认派给 chef
    const targetPos = matchedRule ? matchedRule.handlerPosition : 'chef';
    const handler   = (window.mockData.employees || [])
        .find(e => e.status === 'active' && e.orgId === d.orgId && e.position === targetPos)
        || (window.mockData.employees || []).find(e => e.status === 'active' && e.orgId === d.orgId)
        || { id: 1, name: '赵厨长' };

    const now        = _evNow();
    const deadline   = _evAddHours(now, 24);
    const note       = matchedRule ? matchedRule.note : '一般投诉';

    d.assignType   = 'auto';
    d.handleStatus = 'assigned';
    d.assignerId   = 0;
    d.assignerName = '系统自动';
    d.handlerId    = handler.id;
    d.handlerName  = handler.name;
    d.assignTime   = now;
    d.deadline     = deadline;
    d.assignRemark = `${note}，自动派单至${handler.name}`;
    d.updatedAt    = now;
    d.logs = d.logs || [];
    d.logs.push({ time: now, op: `系统自动派单至${handler.name}（${note}），截止 ${deadline}` });

    showToast(`【${d.docNo}】已自动派单至 ${handler.name}，处理截止 ${deadline}`);
    _refreshEvPage();
}

/* ============================================================
   人工派单弹窗
   ============================================================ */
function openManualAssign(id) {
    const d = (window.mockData.evaComplaints || []).find(x => x.id === id);
    if (!d) return;

    const defaultDdl = _evAddHours(_evNow(), 24).slice(0, 16);
    const empOpts = (window.mockData.employees || [])
        .filter(e => e.status === 'active')
        .map(e => `<option value="${e.id}" data-name="${e.name}">${e.name}（${e.orgName}）</option>`)
        .join('');

    showModal(`
        <div class="modal-title">人工派单 — ${d.docNo}</div>
        <div style="background:#f4f7fd;border-radius:4px;padding:8px 12px;margin-bottom:14px;font-size:13px;color:#606266;">
            <span class="tag ${EV_DOC_TYPE_MAP[d.docType]?.cls||'tag-info'}">${EV_DOC_TYPE_MAP[d.docType]?.label||d.docType}</span>
            &nbsp;${d.title}&nbsp;·&nbsp;${d.orgName}
        </div>
        <div style="display:grid;grid-template-columns:100px 1fr;gap:12px;align-items:center;margin-bottom:4px;">
            <label style="font-size:14px;color:#606266;text-align:right;"><span style="color:#f56c6c;">*</span> 处理人员：</label>
            <select id="maHandler" class="filter-input" style="width:100%;">
                <option value="">请选择处理人员</option>
                ${empOpts}
            </select>

            <label style="font-size:14px;color:#606266;text-align:right;"><span style="color:#f56c6c;">*</span> 截止时间：</label>
            <input type="datetime-local" id="maDeadline" class="filter-input" value="${defaultDdl}" style="width:100%;">

            <label style="font-size:14px;color:#606266;text-align:right;">优先级：</label>
            <select id="maPriority" class="filter-input" style="width:160px;">
                <option value="low">低</option>
                <option value="medium" selected>中</option>
                <option value="high">高</option>
            </select>

            <label style="font-size:14px;color:#606266;text-align:right;">派单备注：</label>
            <textarea id="maRemark" rows="3" class="filter-input" style="width:100%;resize:none;"
                      placeholder="可填写派单说明（选填）"></textarea>
        </div>
        <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
            <button class="btn btn-default" onclick="closeModal()">取消</button>
            <button class="btn btn-primary" onclick="submitManualAssign(${id})">确认派单</button>
        </div>
    `, '500px');
}

function submitManualAssign(id) {
    const handlerSel = document.getElementById('maHandler');
    const deadlineEl = document.getElementById('maDeadline');
    const priorityEl = document.getElementById('maPriority');
    const remarkEl   = document.getElementById('maRemark');
    if (!handlerSel || !deadlineEl) return;

    if (!handlerSel.value) { showToast('请选择处理人员', 'error'); return; }
    if (!deadlineEl.value) { showToast('请选择处理截止时间', 'error'); return; }

    const handlerOpt  = handlerSel.options[handlerSel.selectedIndex];
    const handlerName = handlerOpt.getAttribute('data-name') || handlerOpt.text;
    const deadline    = deadlineEl.value.replace('T', ' ') + ':00';
    const priority    = priorityEl ? priorityEl.value : 'medium';
    const remark      = remarkEl   ? remarkEl.value.trim() : '';
    const now         = _evNow();

    const d = (window.mockData.evaComplaints || []).find(x => x.id === id);
    if (!d) return;

    d.assignType   = 'manual';
    d.handleStatus = 'assigned';
    d.assignerId   = 1;
    d.assignerName = '赵厨长';
    d.handlerId    = parseInt(handlerSel.value);
    d.handlerName  = handlerName;
    d.assignTime   = now;
    d.deadline     = deadline;
    d.priority     = priority;
    d.assignRemark = remark || `人工指定 ${handlerName} 处理`;
    d.updatedAt    = now;
    d.logs = d.logs || [];
    d.logs.push({ time: now, op: `管理员人工派单至${handlerName}，截止 ${deadline}` });

    closeModal();
    showToast(`【${d.docNo}】人工派单成功，已通知处理人员 ${handlerName}`);
    _refreshEvPage();
}

/* ============================================================
   派单记录 Tab
   ============================================================ */
function _renderAssignTab(container) {
    const assigned = (window.mockData.evaComplaints || [])
        .filter(d => d.assignType !== 'none')
        .sort((a, b) => (b.assignTime || '').localeCompare(a.assignTime || ''));

    const total    = assigned.length;
    const ps       = window.asPageSize;
    const page     = window.asPage;
    const pages    = Math.max(1, Math.ceil(total / ps));
    const pageData = assigned.slice((page-1)*ps, page*ps);

    let rows = '';
    if (pageData.length === 0) {
        rows = `<tr><td colspan="9" style="text-align:center;color:#999;padding:32px;">暂无派单记录</td></tr>`;
    } else {
        pageData.forEach(d => {
            const dt  = EV_DOC_TYPE_MAP[d.docType] || { label: d.docType, cls: 'tag-info' };
            const st  = EV_STATUS_MAP[d.handleStatus] || { label: d.handleStatus, cls: 'tag-info' };
            const at  = EV_ASSIGN_MAP[d.assignType]   || { label: d.assignType,   cls: 'tag-info' };
            const pri = EV_PRIORITY_MAP[d.priority]   || { label: d.priority, color: '#909399' };
            rows += `<tr>
                <td style="font-family:monospace;font-size:12px;">${d.docNo}</td>
                <td><span class="tag ${dt.cls}">${dt.label}</span></td>
                <td style="max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${d.title}</td>
                <td><span class="tag ${at.cls}">${at.label}</span></td>
                <td>${d.assignerName||'系统'}</td>
                <td>${d.handlerName||'—'}</td>
                <td style="font-size:12px;">${d.assignTime||'—'}</td>
                <td style="font-size:12px;color:${d.deadline&&new Date()>new Date(d.deadline)?'#f56c6c':'inherit'}">${d.deadline||'—'}</td>
                <td><span class="tag ${st.cls}">${st.label}</span></td>
                <td><span style="color:${pri.color};font-weight:${d.priority==='high'?'700':'400'};">${pri.label}</span></td>
                <td><button class="btn btn-info btn-sm" onclick="showEvDetail(${d.id})">详情</button></td>
            </tr>`;
        });
    }

    container.innerHTML = `
        <table class="data-table">
            <thead><tr>
                <th>单据编号</th><th>类型</th><th>标题</th><th>派单方式</th>
                <th>派单人</th><th>处理人</th><th>派单时间</th><th>截止时间</th>
                <th>处理状态</th><th>优先级</th><th style="width:70px;">操作</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
        <div style="margin-top:12px;display:flex;align-items:center;justify-content:flex-end;gap:8px;">
            <span style="color:#606266;font-size:13px;">共 ${total} 条</span>
            <button class="btn btn-default btn-sm" onclick="asPaginate(${page-1})" ${page<=1?'disabled':''}>‹ 上一页</button>
            <span style="font-size:13px;">第 ${page} / ${pages} 页</span>
            <button class="btn btn-default btn-sm" onclick="asPaginate(${page+1})" ${page>=pages?'disabled':''}>下一页 ›</button>
        </div>`;
}

function asPaginate(p) {
    const total = (window.mockData.evaComplaints||[]).filter(d=>d.assignType!=='none').length;
    const pages = Math.max(1, Math.ceil(total / window.asPageSize));
    window.asPage = Math.max(1, Math.min(p, pages));
    _renderAssignTab(document.getElementById('evTabContent'));
}

/* ============================================================
   工具函数
   ============================================================ */
function _evNow() {
    const d = new Date();
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function _evAddHours(dateStr, h) {
    const d = new Date(dateStr.replace(' ', 'T'));
    d.setHours(d.getHours() + h);
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function _evIR(label, value) {
    return `<div style="display:flex;gap:4px;font-size:13px;align-items:flex-start;">
        <span style="color:#909399;white-space:nowrap;min-width:72px;">${label}：</span>
        <span style="color:#303133;">${value}</span>
    </div>`;
}

function _refreshEvPage() {
    if (window.evActiveTab === 'list') {
        _renderEvRows();
    } else {
        _renderAssignTab(document.getElementById('evTabContent'));
    }
}
