/**
 * utils.js — 公共工具函数 & 全局状态
 *
 * 包含：
 *  - 全局页面状态变量
 *  - Toast 提示
 *  - 状态 Tag 生成
 *  - 弹窗 showModal / closeModal
 *  - AI 悬浮提示框 showAiTooltip
 */

/* ============================================================
   全局状态变量（显式挂载到 window，确保跨文件可访问）
   ============================================================ */
/* 当前激活页面标识 */
window.currentPage = 'warehouse';

/* 仓库管理当前激活的子标签：list | inbound | outbound | stocktake */
window.currentWarehouseTab = 'list';

/* 编辑状态记录（null 表示新增，非 null 表示编辑对应 id） */
window.editingWarehouseId = null;
window.editingMaterialId  = null;
window.editingRecipeId    = null;

/* ============================================================
   Toast 全局提示
   用法：showToast('操作成功')  / showToast('出错', 'error')
   ============================================================ */
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.style.background = type === 'success' ? '#67c23a' : '#f56c6c';
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 3000);
}

/* ============================================================
   状态 Tag 生成
   根据状态字符串返回对应 <span class="tag ..."> HTML 字符串
   ============================================================ */
function getStatusTag(status) {
    const map = {
        '使用中':  'tag-success',
        '停用':    'tag-danger',
        '闲置':    'tag-info',
        '待审核':  'tag-warning',
        '已完成':  'tag-success',
        '正常':    'tag-success',
        '库存不足': 'tag-warning',
        '库存积压': 'tag-primary',
        '已过期':  'tag-danger',
        '待完成':  'tag-warning',
        '已驳回':  'tag-danger'
    };
    return `<span class="tag ${map[status] || 'tag-info'}">${status}</span>`;
}

/* ============================================================
   弹窗 showModal / closeModal
   ============================================================ */

/**
 * 打开弹窗
 * @param {string} content - 弹窗内部完整 HTML（含 header/body/footer）
 * @param {string} width   - 弹窗宽度，默认 '600px'
 */
function showModal(content, width = '600px') {
    const overlay = document.getElementById('modalOverlay');
    const modal   = document.getElementById('modal');
    modal.style.width = width;
    modal.innerHTML   = content;
    overlay.classList.add('show');
    overlay.style.display = 'flex';
    // 点击遮罩层空白处关闭弹窗
    overlay.onclick = function (e) {
        if (e.target === overlay) closeModal();
    };
}

/** 关闭弹窗 */
function closeModal() {
    const overlay = document.getElementById('modalOverlay');
    overlay.classList.remove('show');
    overlay.style.display = 'none';
}

/* ============================================================
   AI 悬浮提示框
   ============================================================ */

/**
 * 在指定按钮下方弹出 AI 建议提示框，4 秒后自动消失
 * @param {string} text  - 提示内容
 * @param {Event}  event - 触发事件（用于定位）
 */
function showAiTooltip(text, event) {
    const tooltip = document.getElementById('aiTooltip');
    tooltip.textContent = text;
    tooltip.classList.add('show');
    const rect = event.target.getBoundingClientRect();
    tooltip.style.top  = (rect.bottom + 8) + 'px';
    tooltip.style.left = rect.left + 'px';
    setTimeout(() => tooltip.classList.remove('show'), 4000);
}
