/**
 * mock-data.js — 模拟后端接口数据
 *
 * 说明：
 *  - 所有数据存放在全局对象 mockData 中，供各模块 JS 文件读写
 *  - 增删改操作直接操作此对象（内存），刷新页面数据恢复初始值
 */

/* 显式挂载到 window 对象，确保 file:// 协议下跨文件可访问 */
window.mockData = {

    /* ===================== 仓库列表（字段对齐 wms_warehouse 表）===================== */
    warehouses: [
        {
            id: 1,
            warehouseCode: 'WH-001',
            warehouseName: '主食材冷藏库',
            warehouseType: 'cold',
            maxCapacity: 200,
            capacityUnit: '平方米',
            usedCapacity: 150,
            location: '食堂后楼一层A区',
            managerId: 1,
            managerName: '张三',
            managerPhone: '13800138001',
            status: 'active',
            remark: '主要存放新鲜蔬菜、水果等食材',
            createdAt: '2026-01-10 08:00:00',
            positionTotal: 20,
            positionUsed: 14,
            positionIdle: 6,
            temperature: 3.2,
            humidity: 75.0,
            tempMin: 0, tempMax: 8,
            humidityMin: 60, humidityMax: 90,
            tempStatus: 'normal',
            humidityStatus: 'normal'
        },
        {
            id: 2,
            warehouseCode: 'WH-002',
            warehouseName: '干货常温库',
            warehouseType: 'dry',
            maxCapacity: 200,
            capacityUnit: '平方米',
            usedCapacity: 90,
            location: '食堂后楼一层B区',
            managerId: 2,
            managerName: '李四',
            managerPhone: '13900139002',
            status: 'active',
            remark: '存放米、面、调料等干货食材',
            createdAt: '2026-01-10 08:30:00',
            positionTotal: 15,
            positionUsed: 8,
            positionIdle: 7,
            temperature: 22.5,
            humidity: 50.0,
            tempMin: 15, tempMax: 28,
            humidityMin: 40, humidityMax: 65,
            tempStatus: 'normal',
            humidityStatus: 'normal'
        },
        {
            id: 3,
            warehouseCode: 'WH-003',
            warehouseName: '冷冻肉类库',
            warehouseType: 'freeze',
            maxCapacity: 200,
            capacityUnit: '平方米',
            usedCapacity: 176,
            location: '食堂后楼地下一层',
            managerId: 3,
            managerName: '王五',
            managerPhone: '13700137003',
            status: 'active',
            remark: '专用于冷冻肉类、水产品存储',
            createdAt: '2026-01-10 09:00:00',
            positionTotal: 12,
            positionUsed: 11,
            positionIdle: 1,
            temperature: -18.0,
            humidity: 85.0,
            tempMin: -25, tempMax: -15,
            humidityMin: 80, humidityMax: 95,
            tempStatus: 'normal',
            humidityStatus: 'warning'
        },
        {
            id: 4,
            warehouseCode: 'WH-004',
            warehouseName: '备用常温库',
            warehouseType: 'normal',
            maxCapacity: 100,
            capacityUnit: '平方米',
            usedCapacity: 0,
            location: '食堂后楼二层C区',
            managerId: 4,
            managerName: '赵六',
            managerPhone: '13600136004',
            status: 'inactive',
            remark: '备用仓库，暂未启用',
            createdAt: '2026-01-12 10:00:00',
            positionTotal: 8,
            positionUsed: 0,
            positionIdle: 8,
            temperature: null,
            humidity: null,
            tempMin: 15, tempMax: 28,
            humidityMin: 40, humidityMax: 70,
            tempStatus: 'normal',
            humidityStatus: 'normal'
        }
    ],

    /* ===================== 仓位列表（字段对齐 wms_location 表）===================== */
    locations: [
        // 仓库1（主食材冷藏库）的仓位
        {
            id: 101, locationCode: 'WH001-A01', locationName: 'A区01号货位',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            locationType: '货架位',
            areaCode: 'A', shelfCode: '01', levelCode: '01',
            capacity: 500, capacityUnit: 'kg', usedCapacity: 380,
            temperatureMin: 0, temperatureMax: 8,
            humidityMin: 60, humidityMax: 90,
            materialTypes: '蔬菜,水果',
            status: 'available',   // available=可用 occupied=占用 maintenance=维护中
            remark: '存放叶菜类',
            createdAt: '2026-01-15 09:00:00'
        },
        {
            id: 102, locationCode: 'WH001-A02', locationName: 'A区02号货位',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            locationType: '货架位',
            areaCode: 'A', shelfCode: '01', levelCode: '02',
            capacity: 500, capacityUnit: 'kg', usedCapacity: 420,
            temperatureMin: 0, temperatureMax: 8,
            humidityMin: 60, humidityMax: 90,
            materialTypes: '蔬菜,水果',
            status: 'occupied',
            remark: '存放根茎类',
            createdAt: '2026-01-15 09:10:00'
        },
        {
            id: 103, locationCode: 'WH001-B01', locationName: 'B区01号货位',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            locationType: '托盘位',
            areaCode: 'B', shelfCode: '01', levelCode: '01',
            capacity: 300, capacityUnit: 'kg', usedCapacity: 0,
            temperatureMin: 2, temperatureMax: 6,
            humidityMin: 65, humidityMax: 85,
            materialTypes: '乳制品',
            status: 'available',
            remark: '',
            createdAt: '2026-01-15 09:20:00'
        },
        // 仓库2（干货常温库）的仓位
        {
            id: 201, locationCode: 'WH002-A01', locationName: 'A区01号货位',
            warehouseId: 2, warehouseName: '干货常温库',
            locationType: '货架位',
            areaCode: 'A', shelfCode: '01', levelCode: '01',
            capacity: 1000, capacityUnit: 'kg', usedCapacity: 650,
            temperatureMin: 15, temperatureMax: 28,
            humidityMin: 40, humidityMax: 65,
            materialTypes: '粮油,干货',
            status: 'occupied',
            remark: '米面存储区',
            createdAt: '2026-01-15 10:00:00'
        },
        {
            id: 202, locationCode: 'WH002-B01', locationName: 'B区01号货位',
            warehouseId: 2, warehouseName: '干货常温库',
            locationType: '货架位',
            areaCode: 'B', shelfCode: '01', levelCode: '01',
            capacity: 500, capacityUnit: 'kg', usedCapacity: 200,
            temperatureMin: 15, temperatureMax: 28,
            humidityMin: 40, humidityMax: 65,
            materialTypes: '调料',
            status: 'available',
            remark: '调料存储区',
            createdAt: '2026-01-15 10:15:00'
        },
        // 仓库3（冷冻肉类库）的仓位
        {
            id: 301, locationCode: 'WH003-A01', locationName: 'A区01号货位',
            warehouseId: 3, warehouseName: '冷冻肉类库',
            locationType: '冷冻格',
            areaCode: 'A', shelfCode: '01', levelCode: '01',
            capacity: 800, capacityUnit: 'kg', usedCapacity: 780,
            temperatureMin: -25, temperatureMax: -15,
            humidityMin: 80, humidityMax: 95,
            materialTypes: '肉类,水产',
            status: 'occupied',
            remark: '猪肉专区',
            createdAt: '2026-01-15 11:00:00'
        },
        {
            id: 302, locationCode: 'WH003-A02', locationName: 'A区02号货位',
            warehouseId: 3, warehouseName: '冷冻肉类库',
            locationType: '冷冻格',
            areaCode: 'A', shelfCode: '01', levelCode: '02',
            capacity: 600, capacityUnit: 'kg', usedCapacity: 580,
            temperatureMin: -25, temperatureMax: -15,
            humidityMin: 80, humidityMax: 95,
            materialTypes: '水产',
            status: 'occupied',
            remark: '水产专区',
            createdAt: '2026-01-15 11:10:00'
        },
        {
            id: 303, locationCode: 'WH003-B01', locationName: 'B区01号货位',
            warehouseId: 3, warehouseName: '冷冻肉类库',
            locationType: '冷冻格',
            areaCode: 'B', shelfCode: '01', levelCode: '01',
            capacity: 400, capacityUnit: 'kg', usedCapacity: 50,
            temperatureMin: -25, temperatureMax: -15,
            humidityMin: 80, humidityMax: 95,
            materialTypes: '禽类',
            status: 'available',
            remark: '禽类备用区',
            createdAt: '2026-01-15 11:20:00'
        }
        // 仓库4（备用库）暂无仓位
    ],

    /* ===================== 入库单 ===================== */
    inboundOrders: [
        {
            id: 1, code: 'RK-20260318-001', type: '采购入库',
            supplier: '绿源食品有限公司', warehouse: '主食材冷藏库',
            quantity: '50kg', date: '2026-03-18', status: '待审核'
        },
        {
            id: 2, code: 'RK-20260317-002', type: '采购入库',
            supplier: '鲜达农产品', warehouse: '干货常温库',
            quantity: '30kg', date: '2026-03-17', status: '已完成'
        },
        {
            id: 3, code: 'RK-20260316-003', type: '盘盈入库',
            supplier: '-', warehouse: '冷冻肉类库',
            quantity: '15kg', date: '2026-03-16', status: '已完成'
        }
    ],

    /* ===================== 出库单 ===================== */
    outboundOrders: [
        {
            id: 1, code: 'CK-20260318-001', type: '领用出库',
            dept: '后厨一区', warehouse: '主食材冷藏库',
            quantity: '20kg', date: '2026-03-18', status: '待审核'
        },
        {
            id: 2, code: 'CK-20260317-002', type: '领用出库',
            dept: '后厨二区', warehouse: '干货常温库',
            quantity: '10kg', date: '2026-03-17', status: '已完成'
        },
        {
            id: 3, code: 'CK-20260316-003', type: '报废出库',
            dept: '仓储部', warehouse: '冷冻肉类库',
            quantity: '5kg', date: '2026-03-16', status: '已完成'
        }
    ],

    /* ===================== 盘点单 ===================== */
    stocktakeOrders: [
        {
            id: 1, code: 'PD-20260318-001', warehouse: '主食材冷藏库',
            date: '2026-03-18', person: '张三',
            materialCount: '15种', diff: '+2kg', status: '待审核'
        },
        {
            id: 2, code: 'PD-20260310-002', warehouse: '干货常温库',
            date: '2026-03-10', person: '李四',
            materialCount: '20种', diff: '-1kg', status: '已完成'
        },
        {
            id: 3, code: 'PD-20260301-003', warehouse: '冷冻肉类库',
            date: '2026-03-01', person: '王五',
            materialCount: '10种', diff: '0', status: '已完成'
        }
    ],

    /* ===================== 物料列表 ===================== */
    materials: [
        {
            id: 1, emoji: '🥦', name: '西兰花', code: 'MAT-001',
            spec: '500g/袋', category: '蔬菜', shelfLife: 7,
            stock: 15, unit: '袋', minStock: 10, maxStock: 50, stockStatus: '正常',
            expireRemindDays: 2, warnDays: 3,
            storageReq: '冷藏 0-4℃，避免挤压',
            expiryDate: '2026-03-25',
            remark: '优先使用当日到货批次',
            createdAt: '2026-01-10 08:00:00'
        },
        {
            id: 2, emoji: '🥩', name: '猪里脊', code: 'MAT-002',
            spec: '1kg/块', category: '肉类', shelfLife: 3,
            stock: 5, unit: '块', minStock: 10, maxStock: 30, stockStatus: '库存不足',
            expireRemindDays: 1, warnDays: 2,
            storageReq: '冷藏 0-4℃，当日使用',
            expiryDate: '2026-03-20',
            remark: '需检查检疫证明',
            createdAt: '2026-01-10 09:00:00'
        },
        {
            id: 3, emoji: '🐟', name: '草鱼', code: 'MAT-003',
            spec: '1条/条', category: '水产', shelfLife: 2,
            stock: 0, unit: '条', minStock: 5, maxStock: 20, stockStatus: '已过期',
            expireRemindDays: 1, warnDays: 1,
            storageReq: '冷藏 0-2℃，活鱼暂养',
            expiryDate: '2026-03-16',
            remark: '已过期，待处理',
            createdAt: '2026-01-11 08:00:00'
        },
        {
            id: 4, emoji: '🧂', name: '食盐', code: 'MAT-004',
            spec: '500g/袋', category: '调料', shelfLife: 365,
            stock: 80, unit: '袋', minStock: 20, maxStock: 50, stockStatus: '库存积压',
            expireRemindDays: 30, warnDays: 60,
            storageReq: '常温干燥处存放，防潮',
            expiryDate: '2027-01-10',
            remark: '库存超出上限，暂停采购',
            createdAt: '2026-01-12 10:00:00'
        },
        {
            id: 5, emoji: '🌾', name: '大米', code: 'MAT-005',
            spec: '25kg/袋', category: '粮油', shelfLife: 180,
            stock: 30, unit: '袋', minStock: 10, maxStock: 40, stockStatus: '正常',
            expireRemindDays: 15, warnDays: 30,
            storageReq: '常温干燥，防虫防鼠',
            expiryDate: '2026-09-10',
            remark: '东北粳米，优质品种',
            createdAt: '2026-01-15 08:00:00'
        },
        {
            id: 6, emoji: '🥕', name: '胡萝卜', code: 'MAT-006',
            spec: '500g/袋', category: '蔬菜', shelfLife: 14,
            stock: 8, unit: '袋', minStock: 10, maxStock: 30, stockStatus: '库存不足',
            expireRemindDays: 3, warnDays: 5,
            storageReq: '冷藏 2-8℃',
            expiryDate: '2026-03-28',
            remark: '需补货',
            createdAt: '2026-01-16 09:00:00'
        },
        {
            id: 7, emoji: '🫚', name: '菜籽油', code: 'MAT-007',
            spec: '5L/桶', category: '粮油', shelfLife: 540,
            stock: 12, unit: '桶', minStock: 5, maxStock: 20, stockStatus: '正常',
            expireRemindDays: 30, warnDays: 60,
            storageReq: '常温避光存放',
            expiryDate: '2027-08-01',
            remark: '非转基因压榨菜籽油',
            createdAt: '2026-01-18 10:00:00'
        },
        {
            id: 8, emoji: '🍗', name: '鸡腿', code: 'MAT-008',
            spec: '1kg/袋', category: '肉类', shelfLife: 5,
            stock: 3, unit: '袋', minStock: 10, maxStock: 30, stockStatus: '已过期',
            expireRemindDays: 2, warnDays: 3,
            storageReq: '冷冻 -18℃以下',
            expiryDate: '2026-03-15',
            remark: '已过期，需报废处理',
            createdAt: '2026-01-20 08:00:00'
        }
    ],

    /* ===================== 菜谱列表（字段对齐 recipe_menu + recipe_menu_ingredient 表）===================== */
    recipes: [
        {
            id: 1, name: '红烧肉', category: '荤菜',
            cookTime: 45, temperature: 95,
            calories: 520, protein: 28, carbs: 15, fat: 38,
            sodium: 680, fiber: 0.5, nutritionScore: 72, isNew: false,
            method: '五花肉切块焯水，锅中放油炒糖色，下肉块翻炒上色，加料酒、生抽、老抽、八角、桂皮，加水没过肉，大火烧开转小火炖40分钟，收汁装盘。',
            cookingSteps: [
                '五花肉切3cm方块，冷水下锅焯水，撇去浮沫捞出备用',
                '锅中放少量油，加冰糖小火炒至焦糖色',
                '下肉块翻炒均匀上色，加料酒、生抽、老抽调味',
                '加入八角、桂皮、姜片，加热水没过肉块',
                '大火烧开后转小火炖40分钟，大火收汁至浓稠'
            ],
            ingredients: [
                { materialId: 2, materialName: '猪里脊', spec: '1kg/块', quantity: 500, unit: 'g', isMain: true, remark: '选五花肉更佳' },
                { materialId: 4, materialName: '食盐', spec: '500g/袋', quantity: 5, unit: 'g', isMain: false, remark: '' }
            ],
            updateTime: '2026-03-15'
        },
        {
            id: 2, name: '清炒西兰花', category: '素菜',
            cookTime: 8, temperature: 180,
            calories: 85, protein: 5, carbs: 8, fat: 4,
            sodium: 320, fiber: 3.2, nutritionScore: 91, isNew: false,
            method: '西兰花掰成小朵焯水，热锅冷油下蒜末爆香，下西兰花大火翻炒，加盐调味出锅。',
            cookingSteps: [
                '西兰花掰成小朵，加盐焯水1分钟捞出过凉水',
                '热锅冷油，下蒜末爆香',
                '下西兰花大火翻炒2分钟',
                '加盐、少许鸡精调味，出锅装盘'
            ],
            ingredients: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', quantity: 2, unit: '袋', isMain: true, remark: '' },
                { materialId: 4, materialName: '食盐', spec: '500g/袋', quantity: 3, unit: 'g', isMain: false, remark: '' }
            ],
            updateTime: '2026-03-14'
        },
        {
            id: 3, name: '番茄蛋花汤', category: '汤类',
            cookTime: 15, temperature: 100,
            calories: 120, protein: 8, carbs: 10, fat: 5,
            sodium: 420, fiber: 1.1, nutritionScore: 85, isNew: false,
            method: '番茄切块，鸡蛋打散。锅中加水烧开，下番茄煮软，淋入蛋液搅散，加盐调味，撒葱花出锅。',
            cookingSteps: [
                '番茄洗净切滚刀块，鸡蛋打散备用',
                '锅中加水1000ml烧开',
                '下番茄块煮5分钟至软烂',
                '转小火，缓缓淋入蛋液，用筷子轻轻搅散成蛋花',
                '加盐调味，撒葱花出锅'
            ],
            ingredients: [
                { materialId: 6, materialName: '胡萝卜', spec: '500g/袋', quantity: 1, unit: '袋', isMain: true, remark: '可替换为番茄' },
                { materialId: 4, materialName: '食盐', spec: '500g/袋', quantity: 4, unit: 'g', isMain: false, remark: '' }
            ],
            updateTime: '2026-03-13'
        },
        {
            id: 4, name: '白米饭', category: '主食',
            cookTime: 30, temperature: 100,
            calories: 350, protein: 7, carbs: 77, fat: 1,
            sodium: 5, fiber: 0.4, nutritionScore: 78, isNew: false,
            method: '大米淘洗2遍，加1:1.2比例的水，电饭锅煮饭模式，焖10分钟后开盖翻松。',
            cookingSteps: [
                '大米量取所需份量，淘洗2遍至水清',
                '加入1:1.2比例的清水',
                '电饭锅选择煮饭模式，约25分钟',
                '跳闸后焖10分钟，开盖用饭勺翻松即可'
            ],
            ingredients: [
                { materialId: 5, materialName: '大米', spec: '25kg/袋', quantity: 5, unit: 'kg', isMain: true, remark: '东北粳米口感更佳' }
            ],
            updateTime: '2026-03-12'
        },
        {
            id: 5, name: '清蒸鱼', category: '荤菜',
            cookTime: 12, temperature: 100,
            calories: 180, protein: 25, carbs: 2, fat: 8,
            sodium: 510, fiber: 0, nutritionScore: 88, isNew: true,
            method: '草鱼处理干净，鱼身划刀，抹盐腌制10分钟，放姜片葱段，上锅蒸10分钟，淋热油和蒸鱼豉油。',
            cookingSteps: [
                '草鱼宰杀处理干净，鱼身两侧各划3刀',
                '抹少量盐腌制10分钟去腥',
                '鱼腹内放姜片，鱼身上铺葱段',
                '水开后上锅蒸10分钟',
                '取出去掉葱姜，淋蒸鱼豉油，浇热油激香'
            ],
            ingredients: [
                { materialId: 3, materialName: '草鱼', spec: '1条/条', quantity: 1, unit: '条', isMain: true, remark: '约1.5kg为宜' },
                { materialId: 4, materialName: '食盐', spec: '500g/袋', quantity: 3, unit: 'g', isMain: false, remark: '' }
            ],
            updateTime: '2026-03-11'
        },
        {
            id: 6, name: '麻婆豆腐', category: '荤菜',
            cookTime: 20, temperature: 160,
            calories: 220, protein: 15, carbs: 12, fat: 14,
            sodium: 750, fiber: 0.8, nutritionScore: 76, isNew: false,
            method: '豆腐切块焯水，锅中炒肉末，加豆瓣酱、花椒粉炒香，加水烧开，下豆腐小火炖5分钟，勾芡撒花椒粉出锅。',
            cookingSteps: [
                '豆腐切2cm方块，加盐焯水1分钟捞出',
                '锅中放油，下猪肉末炒散至变色',
                '加豆瓣酱、花椒粉炒出红油',
                '加水300ml烧开，下豆腐小火炖5分钟',
                '水淀粉勾芡，撒花椒粉和葱花出锅'
            ],
            ingredients: [
                { materialId: 2, materialName: '猪里脊', spec: '1kg/块', quantity: 100, unit: 'g', isMain: false, remark: '剁成肉末' },
                { materialId: 4, materialName: '食盐', spec: '500g/袋', quantity: 3, unit: 'g', isMain: false, remark: '' }
            ],
            updateTime: '2026-03-10'
        }
    ],

    /* ===================== 组织列表（字段对齐 sys_organization 表）===================== */
    orgs: [
        {
            id: 1, orgCode: 'ORG-001', orgName: '智慧食安集团总部',
            orgType: 'group', parentId: 0, level: 1, path: '/',
            leaderName: '张总', contactPhone: '010-88888888',
            address: '北京市朝阳区建国路88号',
            status: 'active', sortOrder: 1, tenantId: 1,
            createdAt: '2026-01-01 08:00:00', updatedAt: '2026-01-01 08:00:00'
        },
        {
            id: 2, orgCode: 'ORG-002', orgName: '华东区分公司',
            orgType: 'company', parentId: 1, level: 2, path: '/1/',
            leaderName: '李经理', contactPhone: '021-66666666',
            address: '上海市浦东新区张江高科技园区',
            status: 'active', sortOrder: 1, tenantId: 1,
            createdAt: '2026-01-05 09:00:00', updatedAt: '2026-01-05 09:00:00'
        },
        {
            id: 3, orgCode: 'ORG-003', orgName: '第一食堂',
            orgType: 'canteen', parentId: 2, level: 3, path: '/1/2/',
            leaderName: '王主任', contactPhone: '021-55555001',
            address: '上海市浦东新区张江路100号园区食堂楼',
            status: 'active', sortOrder: 1, tenantId: 1,
            createdAt: '2026-01-10 08:00:00', updatedAt: '2026-01-10 08:00:00'
        },
        {
            id: 4, orgCode: 'ORG-004', orgName: '后厨管理部',
            orgType: 'dept', parentId: 3, level: 4, path: '/1/2/3/',
            leaderName: '赵厨长', contactPhone: '021-55555002',
            address: '第一食堂后厨区域',
            status: 'active', sortOrder: 1, tenantId: 1,
            createdAt: '2026-01-10 09:00:00', updatedAt: '2026-01-10 09:00:00'
        },
        {
            id: 5, orgCode: 'ORG-005', orgName: '仓储管理部',
            orgType: 'dept', parentId: 3, level: 4, path: '/1/2/3/',
            leaderName: '钱仓管', contactPhone: '021-55555003',
            address: '第一食堂仓储区域',
            status: 'inactive', sortOrder: 2, tenantId: 1,
            createdAt: '2026-01-12 10:00:00', updatedAt: '2026-02-01 10:00:00'
        }
    ],

    /* ===================== 入库单列表（字段对齐 wms_inbound_order 表）===================== */
    inboundOrders: [
        {
            id: 1, orderNo: 'IB-20260322-001',
            refType: 'purchase', refNo: 'PO-2026032201',
            supplierId: 2, supplierName: '华丰粮油贸易公司',
            warehouseId: 2, warehouseName: '干货常温库',
            inboundDate: '2026-03-24',
            operatorId: 1, operatorName: '张三',
            remark: '月度粮油定期入库',
            status: 'approved',
            auditAt: '2026-03-24 16:00:00', auditRemark: '物料与采购订单一致，审核通过',
            tenantId: 1,
            createdAt: '2026-03-24 14:00:00', updatedAt: '2026-03-24 16:00:00',
            items: [
                { materialId: 5, materialName: '大米',   spec: '25kg/袋', unit: '袋', quantity: 20, unitPrice: 98.00, batchNo: 'BATCH-20260324', expiryDate: '2026-12-01', remark: '东北粳米' },
                { materialId: 7, materialName: '菜籽油', spec: '5L/桶',   unit: '桶', quantity: 10, unitPrice: 69.00, batchNo: 'BATCH-20260324', expiryDate: '2027-10-01', remark: '' }
            ]
        },
        {
            id: 2, orderNo: 'IB-20260322-002',
            refType: 'purchase', refNo: 'PO-2026032301',
            supplierId: 1, supplierName: '鲜达农产品有限公司',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            inboundDate: '2026-03-25',
            operatorId: 1, operatorName: '张三',
            remark: '春季蔬菜批次到货',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-25 09:00:00', updatedAt: '2026-03-25 09:00:00',
            items: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', quantity: 50, unitPrice: 8.50,  batchNo: 'BATCH-20260325A', expiryDate: '2026-04-05', remark: '' },
                { materialId: 6, materialName: '胡萝卜', spec: '1kg/袋',  unit: '袋', quantity: 30, unitPrice: 5.00,  batchNo: 'BATCH-20260325A', expiryDate: '2026-04-10', remark: '' },
                { materialId: 8, materialName: '鸡腿',   spec: '1kg/袋',  unit: 'kg', quantity: 50, unitPrice: 39.90, batchNo: 'BATCH-20260325B', expiryDate: '2026-09-25', remark: '冷鲜鸡腿' }
            ]
        },
        {
            id: 3, orderNo: 'IB-20260321-001',
            refType: 'purchase', refNo: 'PO-2026032101',
            supplierId: 3, supplierName: '冷链肉品配送中心',
            warehouseId: 3, warehouseName: '冷冻肉类库',
            inboundDate: '2026-03-22',
            operatorId: 2, operatorName: '李四',
            remark: '肉类周补部分到货',
            status: 'approved',
            auditAt: '2026-03-22 17:00:00', auditRemark: '数量核对无误，审核通过',
            tenantId: 1,
            createdAt: '2026-03-22 15:00:00', updatedAt: '2026-03-22 17:00:00',
            items: [
                { materialId: 2, materialName: '猪里脊', spec: '里脊肉/kg', unit: 'kg', quantity: 50, unitPrice: 38.50, batchNo: 'BATCH-20260321', expiryDate: '2026-04-21', remark: '' },
                { materialId: 8, materialName: '鸡腿',   spec: '1kg/袋',    unit: 'kg', quantity: 60, unitPrice: 41.67, batchNo: 'BATCH-20260321', expiryDate: '2026-09-22', remark: '' }
            ]
        },
        {
            id: 4, orderNo: 'IB-20260318-001',
            refType: 'none', refNo: '',
            supplierId: 1, supplierName: '鲜达农产品有限公司',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            inboundDate: '2026-03-18',
            operatorId: 2, operatorName: '李四',
            remark: '临时补货，无关联采购订单',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-18 10:00:00', updatedAt: '2026-03-18 10:00:00',
            items: [
                { materialId: 3, materialName: '草鱼', spec: '整条/条', unit: '条', quantity: 20, unitPrice: 32.00, batchNo: 'BATCH-20260318', expiryDate: '2026-03-24', remark: '活鱼到货' }
            ]
        },
        {
            id: 5, orderNo: 'IB-20260315-001',
            refType: 'none', refNo: '',
            supplierId: 2, supplierName: '华丰粮油贸易公司',
            warehouseId: 2, warehouseName: '干货常温库',
            inboundDate: '2026-03-15',
            operatorId: 1, operatorName: '张三',
            remark: '已作废测试单据',
            status: 'void', tenantId: 1,
            createdAt: '2026-03-15 11:00:00', updatedAt: '2026-03-16 09:00:00',
            items: [
                { materialId: 4, materialName: '食盐', spec: '500g/袋', unit: '袋', quantity: 100, unitPrice: 2.00, batchNo: 'BATCH-20260315', expiryDate: '2028-03-15', remark: '' }
            ]
        }
    ],

    /* ===================== 采购计划列表（字段对齐 scm_purchase_plan 表）===================== */
    purchasePlans: [
        {
            id: 1, planNo: 'PP-20260323-001', planName: '第一食堂3月第4周蔬菜采购计划',
            orgId: 3, orgName: '第一食堂', period: 'week', planDate: '2026-03-23',
            status: 'approved',
            auditAt: '2026-03-23 10:00:00', auditRemark: '物料清单合理，审核通过',
            remark: '本周蔬菜需求量较大，重点保障西兰花、胡萝卜供应',
            creatorId: 1, creatorName: '张三', tenantId: 1,
            createdAt: '2026-03-20', updatedAt: '2026-03-23 10:00:00',
            items: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', quantity: 60, unitPrice: 8.50, remark: '' },
                { materialId: 6, materialName: '胡萝卜', spec: '1kg/袋', unit: '袋', quantity: 40, unitPrice: 5.00, remark: '' }
            ]
        },
        {
            id: 2, planNo: 'PP-20260323-002', planName: '冷冻肉类月度补货计划',
            orgId: 3, orgName: '第一食堂', period: 'month', planDate: '2026-03-01',
            status: 'submitted',
            remark: '3月肉类月度计划，含猪里脊和鸡腿',
            creatorId: 1, creatorName: '张三', tenantId: 1,
            createdAt: '2026-03-01', updatedAt: '2026-03-22 10:00:00',
            items: [
                { materialId: 2, materialName: '猪里脊', spec: '里脊肉/kg', unit: 'kg', quantity: 200, unitPrice: 38.50, remark: '优先选里脊肉' },
                { materialId: 8, materialName: '鸡腿',   spec: '1kg/袋',   unit: 'kg', quantity: 150, unitPrice: 40.00, remark: '' }
            ]
        },
        {
            id: 3, planNo: 'PP-20260322-003', planName: '粮油日常补货计划',
            orgId: 3, orgName: '第一食堂', period: 'week', planDate: '2026-03-22',
            status: 'draft',
            remark: '',
            creatorId: 2, creatorName: '李四', tenantId: 1,
            createdAt: '2026-03-22', updatedAt: '2026-03-22 09:00:00',
            items: [
                { materialId: 5, materialName: '大米',   spec: '25kg/袋', unit: '袋', quantity: 10, unitPrice: 98.00, remark: '东北粳米' },
                { materialId: 7, materialName: '菜籽油', spec: '5L/桶',   unit: '桶', quantity: 8,  unitPrice: 69.00, remark: '' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: '袋', quantity: 30, unitPrice: 2.00,  remark: '' }
            ]
        },
        {
            id: 4, planNo: 'PP-20260321-004', planName: '水产类日采购计划',
            orgId: 3, orgName: '第一食堂', period: 'day', planDate: '2026-03-21',
            status: 'draft',
            remark: '当日新鲜水产采购，需保证活鱼到货',
            creatorId: 2, creatorName: '李四', tenantId: 1,
            createdAt: '2026-03-21', updatedAt: '2026-03-21 08:00:00',
            items: [
                { materialId: 3, materialName: '草鱼', spec: '整条/条', unit: '条', quantity: 20, unitPrice: 32.00, remark: '活鱼，约1.5kg/条' }
            ]
        },
        {
            id: 5, planNo: 'PP-20260318-005', planName: '后厨管理部调料月计划',
            orgId: 4, orgName: '后厨管理部', period: 'month', planDate: '2026-03-18',
            status: 'submitted',
            remark: '3月调味品统一采购',
            creatorId: 1, creatorName: '张三', tenantId: 1,
            createdAt: '2026-03-18', updatedAt: '2026-03-19 11:00:00',
            items: [
                { materialId: 4, materialName: '食盐',   spec: '1kg/袋',  unit: '袋', quantity: 50, unitPrice: 3.50, remark: '' },
                { materialId: 7, materialName: '菜籽油', spec: '1L/瓶',   unit: '瓶', quantity: 60, unitPrice: 9.80, remark: '' }
            ]
        }
    ],

    /* ===================== 采购订单列表（字段对齐 scm_purchase_order 表）===================== */
    purchaseOrders: [
        {
            id: 1, orderNo: 'PO-2026032301', supplierId: 1, supplierName: '鲜达农产品有限公司',
            orderDate: '2026-03-23', expectedArrival: '2026-03-25',
            totalAmount: 3850.00, status: 'approved',
            buyerId: 1, buyerName: '张三', orgId: 3, orgName: '第一食堂',
            remark: '春季食材补货',
            auditAt: '2026-03-23 10:30:00', auditRemark: '审核通过',
            tenantId: 1, createdAt: '2026-03-23 09:00:00', updatedAt: '2026-03-23 10:30:00',
            items: [
                { id: 1, orderId: 1, materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', quantity: 50, unitPrice: 8.50, totalPrice: 425.00, receivedQty: 0, remark: '' },
                { id: 2, orderId: 1, materialId: 6, materialName: '胡萝卜', spec: '1kg/袋', unit: '袋', quantity: 30, unitPrice: 5.00, totalPrice: 150.00, receivedQty: 0, remark: '' },
                { id: 3, orderId: 1, materialId: 3, materialName: '草鱼', spec: '整条/条', unit: '条', quantity: 40, unitPrice: 32.00, totalPrice: 1280.00, receivedQty: 0, remark: '活鱼' },
                { id: 4, orderId: 1, materialId: 8, materialName: '鸡腿', spec: '1kg/袋', unit: 'kg', quantity: 50, unitPrice: 39.90, totalPrice: 1995.00, receivedQty: 0, remark: '' }
            ]
        },
        {
            id: 2, orderNo: 'PO-2026032201', supplierId: 2, supplierName: '华丰粮油贸易公司',
            orderDate: '2026-03-22', expectedArrival: '2026-03-24',
            totalAmount: 2650.00, status: 'received',
            buyerId: 1, buyerName: '张三', orgId: 3, orgName: '第一食堂',
            remark: '月度粮油补货',
            auditAt: '2026-03-22 11:00:00', auditRemark: '审核通过',
            tenantId: 1, createdAt: '2026-03-22 10:00:00', updatedAt: '2026-03-24 14:00:00',
            items: [
                { id: 5, orderId: 2, materialId: 5, materialName: '大米', spec: '25kg/袋', unit: '袋', quantity: 20, unitPrice: 98.00, totalPrice: 1960.00, receivedQty: 20, remark: '东北粳米' },
                { id: 6, orderId: 2, materialId: 7, materialName: '菜籽油', spec: '5L/桶', unit: '桶', quantity: 10, unitPrice: 69.00, totalPrice: 690.00, receivedQty: 10, remark: '' }
            ]
        },
        {
            id: 3, orderNo: 'PO-2026032101', supplierId: 3, supplierName: '冷链肉品配送中心',
            orderDate: '2026-03-21', expectedArrival: '2026-03-22',
            totalAmount: 5580.00, status: 'partial',
            buyerId: 2, buyerName: '李四', orgId: 3, orgName: '第一食堂',
            remark: '肉类周补',
            auditAt: '2026-03-21 09:30:00', auditRemark: '审核通过',
            tenantId: 1, createdAt: '2026-03-21 08:00:00', updatedAt: '2026-03-22 15:00:00',
            items: [
                { id: 7, orderId: 3, materialId: 2, materialName: '猪里脊', spec: '里脊肉/kg', unit: 'kg', quantity: 80, unitPrice: 38.50, totalPrice: 3080.00, receivedQty: 50, remark: '' },
                { id: 8, orderId: 3, materialId: 8, materialName: '鸡腿', spec: '1kg/袋', unit: 'kg', quantity: 60, unitPrice: 41.67, totalPrice: 2500.00, receivedQty: 60, remark: '' }
            ]
        },
        {
            id: 4, orderNo: 'PO-2026031801', supplierId: 1, supplierName: '鲜达农产品有限公司',
            orderDate: '2026-03-18', expectedArrival: '2026-03-20',
            totalAmount: 1260.00, status: 'draft',
            buyerId: 1, buyerName: '张三', orgId: 3, orgName: '第一食堂',
            remark: '',
            auditAt: null, auditRemark: null,
            tenantId: 1, createdAt: '2026-03-18 16:00:00', updatedAt: '2026-03-18 16:00:00',
            items: [
                { id: 9, orderId: 4, materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', quantity: 80, unitPrice: 8.50, totalPrice: 680.00, receivedQty: 0, remark: '' },
                { id: 10, orderId: 4, materialId: 6, materialName: '胡萝卜', spec: '500g/袋', unit: '袋', quantity: 80, unitPrice: 4.00, totalPrice: 320.00, receivedQty: 0, remark: '' },
                { id: 11, orderId: 4, materialId: 4, materialName: '食盐', spec: '500g/袋', unit: '袋', quantity: 20, unitPrice: 2.00, totalPrice: 260.00, receivedQty: 0, remark: '' }
            ]
        },
        {
            id: 5, orderNo: 'PO-2026031501', supplierId: 2, supplierName: '华丰粮油贸易公司',
            orderDate: '2026-03-15', expectedArrival: '2026-03-17',
            totalAmount: 980.00, status: 'cancelled',
            buyerId: 2, buyerName: '李四', orgId: 3, orgName: '第一食堂',
            remark: '供应商临时无货，已取消',
            auditAt: '2026-03-15 14:00:00', auditRemark: '审核通过',
            tenantId: 1, createdAt: '2026-03-15 10:00:00', updatedAt: '2026-03-16 09:00:00',
            items: [
                { id: 12, orderId: 5, materialId: 7, materialName: '菜籽油', spec: '1L/瓶', unit: '瓶', quantity: 100, unitPrice: 9.80, totalPrice: 980.00, receivedQty: 0, remark: '' }
            ]
        }
    ],

    /* ===================== 库存汇总列表（字段对齐 wms_inventory 表）===================== */
    inventorySummary: [
        {
            id: 1, materialId: 1, materialName: '西兰花', categoryName: '蔬菜',
            spec: '500g/袋', unit: '袋',
            warehouseId: 1, warehouseName: '主食材冷藏库', locationCode: 'WH001-A01',
            currentQty: 45, safeQty: 20, lockQty: 5,
            availableQty: 40,
            avgCostPrice: 8.50, totalValue: 382.50,
            batchNo: 'BATCH-20260322', productionDate: '2026-03-20', expiryDate: '2026-04-02',
            status: 'normal',
            lastInboundAt: '2026-03-22 14:00:00', lastOutboundAt: '2026-03-23 08:30:00',
            tenantId: 1, updatedAt: '2026-03-23 08:30:00'
        },
        {
            id: 2, materialId: 2, materialName: '猪里脊', categoryName: '肉类',
            spec: '里脊肉/kg', unit: 'kg',
            warehouseId: 3, warehouseName: '冷冻肉类库', locationCode: 'WH003-B02',
            currentQty: 50, safeQty: 30, lockQty: 10,
            availableQty: 40,
            avgCostPrice: 38.50, totalValue: 1925.00,
            batchNo: 'BATCH-20260321', productionDate: '2026-03-21', expiryDate: '2026-04-20',
            status: 'normal',
            lastInboundAt: '2026-03-22 15:00:00', lastOutboundAt: '2026-03-23 07:00:00',
            tenantId: 1, updatedAt: '2026-03-23 07:00:00'
        },
        {
            id: 3, materialId: 3, materialName: '草鱼', categoryName: '水产',
            spec: '整条/条', unit: '条',
            warehouseId: 1, warehouseName: '主食材冷藏库', locationCode: 'WH001-C03',
            currentQty: 8, safeQty: 10, lockQty: 0,
            availableQty: 8,
            avgCostPrice: 32.00, totalValue: 256.00,
            batchNo: 'BATCH-20260320', productionDate: '2026-03-20', expiryDate: '2026-03-26',
            status: 'warning',
            lastInboundAt: '2026-03-20 10:00:00', lastOutboundAt: '2026-03-23 09:00:00',
            tenantId: 1, updatedAt: '2026-03-23 09:00:00'
        },
        {
            id: 4, materialId: 4, materialName: '食盐', categoryName: '调料',
            spec: '500g/袋', unit: '袋',
            warehouseId: 2, warehouseName: '干货常温库', locationCode: 'WH002-A01',
            currentQty: 120, safeQty: 30, lockQty: 0,
            availableQty: 120,
            avgCostPrice: 2.00, totalValue: 240.00,
            batchNo: 'BATCH-20260101', productionDate: '2026-01-01', expiryDate: '2028-01-01',
            status: 'normal',
            lastInboundAt: '2026-03-15 14:00:00', lastOutboundAt: '2026-03-22 10:00:00',
            tenantId: 1, updatedAt: '2026-03-22 10:00:00'
        },
        {
            id: 5, materialId: 5, materialName: '大米', categoryName: '粮食',
            spec: '25kg/袋', unit: '袋',
            warehouseId: 2, warehouseName: '干货常温库', locationCode: 'WH002-B01',
            currentQty: 20, safeQty: 5, lockQty: 0,
            availableQty: 20,
            avgCostPrice: 98.00, totalValue: 1960.00,
            batchNo: 'BATCH-20260324', productionDate: '2025-12-01', expiryDate: '2026-12-01',
            status: 'normal',
            lastInboundAt: '2026-03-24 14:00:00', lastOutboundAt: '2026-03-23 11:00:00',
            tenantId: 1, updatedAt: '2026-03-24 14:00:00'
        },
        {
            id: 6, materialId: 7, materialName: '菜籽油', categoryName: '油脂',
            spec: '5L/桶', unit: '桶',
            warehouseId: 2, warehouseName: '干货常温库', locationCode: 'WH002-C02',
            currentQty: 3, safeQty: 5, lockQty: 0,
            availableQty: 3,
            avgCostPrice: 69.00, totalValue: 207.00,
            batchNo: 'BATCH-20260310', productionDate: '2025-10-01', expiryDate: '2026-10-01',
            status: 'shortage',
            lastInboundAt: '2026-03-10 09:00:00', lastOutboundAt: '2026-03-22 16:00:00',
            tenantId: 1, updatedAt: '2026-03-22 16:00:00'
        },
        {
            id: 7, materialId: 8, materialName: '鸡腿', categoryName: '肉类',
            spec: '1kg/袋', unit: 'kg',
            warehouseId: 3, warehouseName: '冷冻肉类库', locationCode: 'WH003-A01',
            currentQty: 60, safeQty: 20, lockQty: 5,
            availableQty: 55,
            avgCostPrice: 40.00, totalValue: 2400.00,
            batchNo: 'BATCH-20260322', productionDate: '2026-03-22', expiryDate: '2026-09-22',
            status: 'normal',
            lastInboundAt: '2026-03-22 15:00:00', lastOutboundAt: '2026-03-23 07:00:00',
            tenantId: 1, updatedAt: '2026-03-23 07:00:00'
        },
        {
            id: 8, materialId: 6, materialName: '胡萝卜', categoryName: '蔬菜',
            spec: '500g/袋', unit: '袋',
            warehouseId: 1, warehouseName: '主食材冷藏库', locationCode: 'WH001-A02',
            currentQty: 0, safeQty: 15, lockQty: 0,
            availableQty: 0,
            avgCostPrice: 4.00, totalValue: 0,
            batchNo: '', productionDate: '', expiryDate: '',
            status: 'out',
            lastInboundAt: '2026-03-15 10:00:00', lastOutboundAt: '2026-03-22 17:00:00',
            tenantId: 1, updatedAt: '2026-03-22 17:00:00'
        }
    ],

    /* ===================== 供应商列表（字段对齐 scm_supplier 表）===================== */
    suppliers: [
        {
            id: 1, supplierCode: 'SUP-001', supplierName: '鲜达农产品有限公司',
            contactName: '陈经理', contactPhone: '13800138001', contactEmail: 'chen@xianda.com',
            address: '上海市嘉定区农产品批发市场A区12号',
            categoryTags: ['蔬菜', '水产'],
            licenseNo: '91310000MA1FL12345', licenseExpiresAt: '2027-06-30',
            foodLicenseNo: 'JY31000020230001', foodLicenseExpiresAt: '2026-04-15',
            creditScore: 92,
            scoreQualification: 95, scoreQuality: 90, scorePrice: 88, scoreDelivery: 94,
            status: 'active',
            auditAt: '2026-01-15 10:00:00', auditRemark: '资质齐全，审核通过',
            tenantId: 1, createdAt: '2026-01-10 09:00:00', updatedAt: '2026-01-15 10:00:00'
        },
        {
            id: 2, supplierCode: 'SUP-002', supplierName: '华丰粮油贸易公司',
            contactName: '李总', contactPhone: '13900139002', contactEmail: 'li@huafeng.com',
            address: '江苏省苏州市工业园区粮油配送中心',
            categoryTags: ['粮油', '调料'],
            licenseNo: '91320000MA1GH67890', licenseExpiresAt: '2028-03-31',
            foodLicenseNo: 'JY32000020230002', foodLicenseExpiresAt: '2027-03-31',
            creditScore: 85,
            scoreQualification: 88, scoreQuality: 82, scorePrice: 90, scoreDelivery: 80,
            status: 'active',
            auditAt: '2026-01-20 14:00:00', auditRemark: '审核通过',
            tenantId: 1, createdAt: '2026-01-12 10:00:00', updatedAt: '2026-01-20 14:00:00'
        },
        {
            id: 3, supplierCode: 'SUP-003', supplierName: '冷链肉品配送中心',
            contactName: '王主任', contactPhone: '13700137003', contactEmail: 'wang@lengchain.com',
            address: '上海市松江区冷链物流园B栋',
            categoryTags: ['肉类', '冷冻食品'],
            licenseNo: '91310000MA1KL11111', licenseExpiresAt: '2026-04-10',
            foodLicenseNo: 'JY31000020230003', foodLicenseExpiresAt: '2026-04-10',
            creditScore: 78,
            scoreQualification: 72, scoreQuality: 80, scorePrice: 75, scoreDelivery: 85,
            status: 'active',
            auditAt: '2026-02-01 09:00:00', auditRemark: '审核通过，注意资质即将到期',
            tenantId: 1, createdAt: '2026-01-18 11:00:00', updatedAt: '2026-02-01 09:00:00'
        },
        {
            id: 4, supplierCode: 'SUP-004', supplierName: '绿源有机蔬菜基地',
            contactName: '赵老板', contactPhone: '13600136004', contactEmail: 'zhao@lvyuan.com',
            address: '浙江省杭州市余杭区有机农业示范园',
            categoryTags: ['蔬菜'],
            licenseNo: '91330000MA1MN22222', licenseExpiresAt: '2027-12-31',
            foodLicenseNo: null, foodLicenseExpiresAt: null,
            creditScore: 100,
            scoreQualification: null, scoreQuality: null, scorePrice: null, scoreDelivery: null,
            status: 'pending',
            auditAt: null, auditRemark: null,
            tenantId: 1, createdAt: '2026-03-15 14:00:00', updatedAt: '2026-03-15 14:00:00'
        },
        {
            id: 5, supplierCode: 'SUP-005', supplierName: '旧城乳品供应商',
            contactName: '孙经理', contactPhone: '13500135005', contactEmail: 'sun@dairy.com',
            address: '北京市顺义区乳品加工园区',
            categoryTags: ['乳制品'],
            licenseNo: '91110000MA1PQ33333', licenseExpiresAt: '2025-12-31',
            foodLicenseNo: 'JY11000020220005', foodLicenseExpiresAt: '2025-12-31',
            creditScore: 60,
            scoreQualification: 55, scoreQuality: 62, scorePrice: 65, scoreDelivery: 58,
            status: 'rejected',
            auditAt: '2026-02-10 16:00:00', auditRemark: '营业执照已过期，驳回',
            tenantId: 1, createdAt: '2026-02-05 10:00:00', updatedAt: '2026-02-10 16:00:00'
        }
    ],

    /* ===================== 出库单列表（字段对齐 wms_outbound_order 表）===================== */
    outboundOrders: [
        {
            id: 1, orderNo: 'OB-20260324-001',
            outboundType: 'pick',
            refNo: '',
            warehouseId: 2, warehouseName: '干货常温库',
            deptId: 4, deptName: '后厨管理部',
            outboundDate: '2026-03-24',
            operatorId: 1, operatorName: '张三',
            remark: '后厨日常领料',
            status: 'approved',
            auditAt: '2026-03-24 17:00:00', auditRemark: '领料清单核对无误，审核通过',
            tenantId: 1,
            createdAt: '2026-03-24 15:00:00', updatedAt: '2026-03-24 17:00:00',
            items: [
                { materialId: 5, materialName: '大米',   spec: '25kg/袋', unit: '袋', quantity: 5,  outReason: 'normal', batchNo: 'BATCH-20260324', remark: '' },
                { materialId: 7, materialName: '菜籽油', spec: '5L/桶',   unit: '桶', quantity: 2,  outReason: 'normal', batchNo: 'BATCH-20260324', remark: '' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: '袋', quantity: 10, outReason: 'normal', batchNo: 'BATCH-20260315', remark: '' }
            ]
        },
        {
            id: 2, orderNo: 'OB-20260325-001',
            outboundType: 'pick',
            refNo: '',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            deptId: 4, deptName: '后厨管理部',
            outboundDate: '2026-03-25',
            operatorId: 1, operatorName: '张三',
            remark: '午餐备料领用',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-25 08:30:00', updatedAt: '2026-03-25 08:30:00',
            items: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', quantity: 20, outReason: 'normal', batchNo: 'BATCH-20260325A', remark: '' },
                { materialId: 6, materialName: '胡萝卜', spec: '1kg/袋',  unit: '袋', quantity: 10, outReason: 'normal', batchNo: 'BATCH-20260325A', remark: '' },
                { materialId: 2, materialName: '猪里脊', spec: '里脊肉/kg', unit: 'kg', quantity: 15, outReason: 'normal', batchNo: 'BATCH-20260321', remark: '' }
            ]
        },
        {
            id: 3, orderNo: 'OB-20260322-001',
            outboundType: 'scrap',
            refNo: '',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            deptId: 5, deptName: '仓储管理部',
            outboundDate: '2026-03-22',
            operatorId: 2, operatorName: '李四',
            remark: '临期食材报废处理',
            status: 'approved',
            auditAt: '2026-03-22 16:30:00', auditRemark: '已核实过期，同意报废',
            tenantId: 1,
            createdAt: '2026-03-22 14:00:00', updatedAt: '2026-03-22 16:30:00',
            items: [
                { materialId: 3, materialName: '草鱼', spec: '整条/条', unit: '条', quantity: 5, outReason: 'scrap', batchNo: 'BATCH-20260318', remark: '已过期' },
                { materialId: 8, materialName: '鸡腿', spec: '1kg/袋',  unit: 'kg', quantity: 8, outReason: 'scrap', batchNo: 'BATCH-20260315', remark: '超保质期处理' }
            ]
        },
        {
            id: 4, orderNo: 'OB-20260320-001',
            outboundType: 'transfer',
            refNo: 'TR-20260320-001',
            warehouseId: 2, warehouseName: '干货常温库',
            deptId: 3, deptName: '第一食堂',
            outboundDate: '2026-03-20',
            operatorId: 2, operatorName: '李四',
            remark: '调拨至二食堂备用',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-20 10:00:00', updatedAt: '2026-03-20 10:00:00',
            items: [
                { materialId: 5, materialName: '大米',   spec: '25kg/袋', unit: '袋', quantity: 3, outReason: 'normal', batchNo: 'BATCH-20260310', remark: '调拨二食堂' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: '袋', quantity: 20, outReason: 'normal', batchNo: 'BATCH-20260315', remark: '' }
            ]
        },
        {
            id: 5, orderNo: 'OB-20260318-001',
            outboundType: 'pick',
            refNo: '',
            warehouseId: 3, warehouseName: '冷冻肉类库',
            deptId: 4, deptName: '后厨管理部',
            outboundDate: '2026-03-18',
            operatorId: 1, operatorName: '张三',
            remark: '已作废测试单据',
            status: 'void', tenantId: 1,
            createdAt: '2026-03-18 09:00:00', updatedAt: '2026-03-19 10:00:00',
            items: [
                { materialId: 2, materialName: '猪里脊', spec: '里脊肉/kg', unit: 'kg', quantity: 10, outReason: 'normal', batchNo: 'BATCH-20260315', remark: '' }
            ]
        }
    ],

    /* ===================== 盘点单列表（字段对齐 wms_stocktake_order 表）===================== */
    stocktakeOrders: [
        {
            id: 1, orderNo: 'ST-20260324-001',
            warehouseId: 2, warehouseName: '干货常温库',
            stocktakeType: 'full',
            stocktakeDate: '2026-03-24',
            operatorId: 1, operatorName: '张三',
            auditorId: 2, auditorName: '李四',
            remark: '月度全盘',
            status: 'approved',
            auditAt: '2026-03-24 18:00:00', auditRemark: '盘点数据核实无误，审核通过',
            tenantId: 1,
            createdAt: '2026-03-24 14:00:00', updatedAt: '2026-03-24 18:00:00',
            items: [
                { materialId: 5, materialName: '大米',   spec: '25kg/袋', unit: '袋', systemQty: 20, actualQty: 20, diffQty: 0,  diffReason: '' },
                { materialId: 7, materialName: '菜籽油', spec: '5L/桶',   unit: '桶', systemQty: 3,  actualQty: 3,  diffQty: 0,  diffReason: '' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: '袋', systemQty: 120, actualQty: 118, diffQty: -2, diffReason: '盘点时发现2袋破损已处理' }
            ]
        },
        {
            id: 2, orderNo: 'ST-20260325-001',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            stocktakeType: 'partial',
            stocktakeDate: '2026-03-25',
            operatorId: 1, operatorName: '张三',
            auditorId: null, auditorName: '',
            remark: '蔬菜品类抽盘',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-25 09:00:00', updatedAt: '2026-03-25 09:00:00',
            items: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', systemQty: 45, actualQty: 43, diffQty: -2, diffReason: '有2袋损耗' },
                { materialId: 6, materialName: '胡萝卜', spec: '1kg/袋',  unit: '袋', systemQty: 0,  actualQty: 0,  diffQty: 0,  diffReason: '' },
                { materialId: 3, materialName: '草鱼',   spec: '整条/条', unit: '条', systemQty: 8,  actualQty: 9,  diffQty: 1,  diffReason: '系统漏录1条' }
            ]
        },
        {
            id: 3, orderNo: 'ST-20260322-001',
            warehouseId: 3, warehouseName: '冷冻肉类库',
            stocktakeType: 'full',
            stocktakeDate: '2026-03-22',
            operatorId: 2, operatorName: '李四',
            auditorId: 2, auditorName: '李四',
            remark: '肉类全盘',
            status: 'approved',
            auditAt: '2026-03-22 17:30:00', auditRemark: '数量吻合，审核通过',
            tenantId: 1,
            createdAt: '2026-03-22 13:00:00', updatedAt: '2026-03-22 17:30:00',
            items: [
                { materialId: 2, materialName: '猪里脊', spec: '里脊肉/kg', unit: 'kg', systemQty: 50, actualQty: 50, diffQty: 0,  diffReason: '' },
                { materialId: 8, materialName: '鸡腿',   spec: '1kg/袋',    unit: 'kg', systemQty: 60, actualQty: 58, diffQty: -2, diffReason: '自然损耗' }
            ]
        },
        {
            id: 4, orderNo: 'ST-20260320-001',
            warehouseId: 2, warehouseName: '干货常温库',
            stocktakeType: 'partial',
            stocktakeDate: '2026-03-20',
            operatorId: 2, operatorName: '李四',
            auditorId: null, auditorName: '',
            remark: '粮油品类抽盘',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-20 10:00:00', updatedAt: '2026-03-20 10:00:00',
            items: [
                { materialId: 5, materialName: '大米',   spec: '25kg/袋', unit: '袋', systemQty: 18, actualQty: 18, diffQty: 0, diffReason: '' },
                { materialId: 7, materialName: '菜籽油', spec: '5L/桶',   unit: '桶', systemQty: 5,  actualQty: 4,  diffQty: -1, diffReason: '一桶已开封消耗' }
            ]
        },
        {
            id: 5, orderNo: 'ST-20260318-001',
            warehouseId: 1, warehouseName: '主食材冷藏库',
            stocktakeType: 'partial',
            stocktakeDate: '2026-03-18',
            operatorId: 1, operatorName: '张三',
            auditorId: null, auditorName: '',
            remark: '已作废测试单据',
            status: 'void', tenantId: 1,
            createdAt: '2026-03-18 09:00:00', updatedAt: '2026-03-19 10:00:00',
            items: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', systemQty: 30, actualQty: 28, diffQty: -2, diffReason: '' }
            ]
        }
    ],

    /* ===================== 菜谱计划列表（字段对齐 menu_plan 表）===================== */
    recipePlans: [
        {
            id: 1, planNo: 'MP-20260324-001', planName: '第一食堂3月第4周午晚餐计划',
            orgId: 3, orgName: '第一食堂',
            period: 'week', planDate: '2026-03-24',
            creatorId: 1, creatorName: '张三',
            auditorId: 2, auditorName: '李四',
            remark: '本周重点推广清蒸鱼和红烧肉',
            status: 'approved',
            auditAt: '2026-03-24 17:00:00', auditRemark: '菜谱搭配合理，营养均衡，审核通过',
            tenantId: 1,
            createdAt: '2026-03-24 10:00:00', updatedAt: '2026-03-24 17:00:00',
            items: [
                { recipeId: 1, recipeName: '红烧肉',    mealTime: 'lunch',  servings: 80, isMain: true,  estCost: 0 },
                { recipeId: 2, recipeName: '清炒西兰花', mealTime: 'lunch',  servings: 100, isMain: false, estCost: 0 },
                { recipeId: 4, recipeName: '白米饭',    mealTime: 'lunch',  servings: 100, isMain: true,  estCost: 0 },
                { recipeId: 5, recipeName: '清蒸鱼',    mealTime: 'dinner', servings: 60,  isMain: true,  estCost: 0 }
            ]
        },
        {
            id: 2, planNo: 'MP-20260325-001', planName: '第一食堂周末特供计划',
            orgId: 3, orgName: '第一食堂',
            period: 'day', planDate: '2026-03-25',
            creatorId: 1, creatorName: '张三',
            auditorId: null, auditorName: '',
            remark: '周末增加特色菜品',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-25 08:00:00', updatedAt: '2026-03-25 08:00:00',
            items: [
                { recipeId: 5, recipeName: '清蒸鱼',   mealTime: 'lunch',   servings: 50,  isMain: true,  estCost: 0 },
                { recipeId: 6, recipeName: '麻婆豆腐', mealTime: 'dinner',  servings: 80,  isMain: false, estCost: 0 },
                { recipeId: 4, recipeName: '白米饭',   mealTime: 'dinner',  servings: 100, isMain: true,  estCost: 0 }
            ]
        },
        {
            id: 3, planNo: 'MP-20260322-001', planName: '第一食堂3月第3周全天计划',
            orgId: 3, orgName: '第一食堂',
            period: 'week', planDate: '2026-03-22',
            creatorId: 2, creatorName: '李四',
            auditorId: 2, auditorName: '李四',
            remark: '',
            status: 'approved',
            auditAt: '2026-03-22 16:00:00', auditRemark: '审核通过',
            tenantId: 1,
            createdAt: '2026-03-22 09:00:00', updatedAt: '2026-03-22 16:00:00',
            items: [
                { recipeId: 3, recipeName: '番茄蛋花汤', mealTime: 'breakfast', servings: 120, isMain: false, estCost: 0 },
                { recipeId: 4, recipeName: '白米饭',     mealTime: 'lunch',     servings: 120, isMain: true,  estCost: 0 },
                { recipeId: 1, recipeName: '红烧肉',     mealTime: 'dinner',    servings: 80,  isMain: true,  estCost: 0 }
            ]
        },
        {
            id: 4, planNo: 'MP-20260320-001', planName: '第一食堂月度养生菜谱计划',
            orgId: 3, orgName: '第一食堂',
            period: 'month', planDate: '2026-03-20',
            creatorId: 1, creatorName: '张三',
            auditorId: null, auditorName: '',
            remark: '以清淡养生为主题',
            status: 'pending', tenantId: 1,
            createdAt: '2026-03-20 10:00:00', updatedAt: '2026-03-20 10:00:00',
            items: [
                { recipeId: 2, recipeName: '清炒西兰花', mealTime: 'lunch',  servings: 100, isMain: false, estCost: 0 },
                { recipeId: 5, recipeName: '清蒸鱼',    mealTime: 'dinner', servings: 60,  isMain: true,  estCost: 0 }
            ]
        },
        {
            id: 5, planNo: 'MP-20260318-001', planName: '已作废测试菜谱计划',
            orgId: 3, orgName: '第一食堂',
            period: 'day', planDate: '2026-03-18',
            creatorId: 1, creatorName: '张三',
            auditorId: null, auditorName: '',
            remark: '测试作废流程',
            status: 'void', tenantId: 1,
            createdAt: '2026-03-18 09:00:00', updatedAt: '2026-03-19 10:00:00',
            items: [
                { recipeId: 4, recipeName: '白米饭', mealTime: 'lunch', servings: 50, isMain: true, estCost: 0 }
            ]
        }
    ],

    /* ===================== 烹饪任务列表（字段对齐 kitchen_cook_task 表）===================== */
    cookTasks: [
        {
            id: 1, taskNo: 'CT-20260324-001',
            planId: 1, planNo: 'MP-20260324-001',
            recipeId: 1, recipeName: '红烧肉',
            orgId: 3, orgName: '第一食堂',
            servings: 80,
            chefId: 1, chefName: '赵厨长',
            mealTime: 'lunch', cookDate: '2026-03-24',
            status: 'done',
            remark: '',
            tenantId: 1,
            createdAt: '2026-03-24 07:00:00', updatedAt: '2026-03-24 12:30:00',
            ingredients: [
                { materialId: 2, materialName: '猪里脊', spec: '1kg/块', unit: 'g',  needQty: 40000, prepDone: true,  prepRemark: '已备料' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: 'g', needQty: 400,   prepDone: true,  prepRemark: '' }
            ]
        },
        {
            id: 2, taskNo: 'CT-20260324-002',
            planId: 1, planNo: 'MP-20260324-001',
            recipeId: 2, recipeName: '清炒西兰花',
            orgId: 3, orgName: '第一食堂',
            servings: 100,
            chefId: 1, chefName: '赵厨长',
            mealTime: 'lunch', cookDate: '2026-03-24',
            status: 'done',
            remark: '',
            tenantId: 1,
            createdAt: '2026-03-24 07:00:00', updatedAt: '2026-03-24 12:00:00',
            ingredients: [
                { materialId: 1, materialName: '西兰花', spec: '500g/袋', unit: '袋', needQty: 200,  prepDone: true,  prepRemark: '' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: 'g',  needQty: 300,  prepDone: true,  prepRemark: '' }
            ]
        },
        {
            id: 3, taskNo: 'CT-20260324-003',
            planId: 1, planNo: 'MP-20260324-001',
            recipeId: 4, recipeName: '白米饭',
            orgId: 3, orgName: '第一食堂',
            servings: 100,
            chefId: 2, chefName: '钱仓管',
            mealTime: 'lunch', cookDate: '2026-03-24',
            status: 'cooking',
            remark: '',
            tenantId: 1,
            createdAt: '2026-03-24 07:00:00', updatedAt: '2026-03-24 10:00:00',
            ingredients: [
                { materialId: 5, materialName: '大米', spec: '25kg/袋', unit: 'kg', needQty: 500, prepDone: true, prepRemark: '东北粳米' }
            ]
        },
        {
            id: 4, taskNo: 'CT-20260324-004',
            planId: 1, planNo: 'MP-20260324-001',
            recipeId: 5, recipeName: '清蒸鱼',
            orgId: 3, orgName: '第一食堂',
            servings: 60,
            chefId: 1, chefName: '赵厨长',
            mealTime: 'dinner', cookDate: '2026-03-24',
            status: 'pending',
            remark: '',
            tenantId: 1,
            createdAt: '2026-03-24 07:00:00', updatedAt: '2026-03-24 07:00:00',
            ingredients: [
                { materialId: 3, materialName: '草鱼', spec: '整条/条', unit: '条', needQty: 60, prepDone: false, prepRemark: '' },
                { materialId: 4, materialName: '食盐', spec: '500g/袋', unit: 'g',  needQty: 180, prepDone: false, prepRemark: '' }
            ]
        },
        {
            id: 5, taskNo: 'CT-20260322-001',
            planId: 3, planNo: 'MP-20260322-001',
            recipeId: 3, recipeName: '番茄蛋花汤',
            orgId: 3, orgName: '第一食堂',
            servings: 120,
            chefId: 2, chefName: '钱仓管',
            mealTime: 'breakfast', cookDate: '2026-03-22',
            status: 'done',
            remark: '已完成',
            tenantId: 1,
            createdAt: '2026-03-22 05:00:00', updatedAt: '2026-03-22 08:00:00',
            ingredients: [
                { materialId: 6, materialName: '胡萝卜', spec: '1kg/袋',  unit: '袋', needQty: 120, prepDone: true, prepRemark: '' },
                { materialId: 4, materialName: '食盐',   spec: '500g/袋', unit: 'g',  needQty: 480, prepDone: true, prepRemark: '' }
            ]
        }
    ],

    /* ===================== 员工管理（employees）===================== */
    employees: [
        {
            id: 1,
            empNo: 'EMP-20260101-001',
            name: '赵厨长',
            gender: 'male',
            phone: '13800138001',
            email: 'zhaocz@example.com',
            idCard: '310101198501015678',
            orgId: 3, orgName: '第一食堂',
            deptId: 4, deptName: '后厨管理部',
            position: 'chef',
            roleIds: [1, 4], roleNames: '超级管理员、厨师长',
            hireDate: '2022-03-01',
            status: 'active',
            remark: '主厨，负责日常菜品研发与出品质量把控',
            createdBy: '管理员', createdAt: '2022-03-01 09:00:00', updatedAt: '2026-01-10 15:00:00'
        },
        {
            id: 2,
            empNo: 'EMP-20260101-002',
            name: '钱仓管',
            gender: 'male',
            phone: '13900139002',
            email: 'qiancg@example.com',
            idCard: '310101199003028765',
            orgId: 3, orgName: '第一食堂',
            deptId: 5, deptName: '仓储管理部',
            position: 'purchaser',
            roleIds: [3], roleNames: '仓管员',
            hireDate: '2023-06-15',
            status: 'active',
            remark: '负责食堂仓储物料管理',
            createdBy: '管理员', createdAt: '2023-06-15 10:00:00', updatedAt: '2025-12-01 09:00:00'
        },
        {
            id: 3,
            empNo: 'EMP-20260101-003',
            name: '孙大厨',
            gender: 'male',
            phone: '15000150003',
            email: 'sundc@example.com',
            idCard: '310101197812159012',
            orgId: 3, orgName: '第一食堂',
            deptId: 4, deptName: '后厨管理部',
            position: 'cookworker',
            roleIds: [4], roleNames: '厨师长',
            hireDate: '2021-09-01',
            status: 'active',
            remark: '擅长粤菜与蒸煮类菜肴',
            createdBy: '管理员', createdAt: '2021-09-01 09:00:00', updatedAt: '2025-09-01 10:00:00'
        },
        {
            id: 4,
            empNo: 'EMP-20260101-004',
            name: '李助厨',
            gender: 'female',
            phone: '18600186004',
            email: '',
            idCard: '310101200001011234',
            orgId: 3, orgName: '第一食堂',
            deptId: 4, deptName: '后厨管理部',
            position: 'cookworker',
            roleIds: [4], roleNames: '厨师长',
            hireDate: '2025-02-10',
            status: 'active',
            remark: '新员工，尚在培训期',
            createdBy: '管理员', createdAt: '2025-02-10 09:00:00', updatedAt: '2025-02-10 09:00:00'
        },
        {
            id: 5,
            empNo: 'EMP-20260101-005',
            name: '王采购',
            gender: 'female',
            phone: '17700177005',
            email: 'wangcg@example.com',
            idCard: '310101199505056789',
            orgId: 2, orgName: '华东区分公司',
            deptId: 0, deptName: '-',
            position: 'purchaser',
            roleIds: [2], roleNames: '采购专员',
            hireDate: '2020-11-01',
            status: 'active',
            remark: '负责华东区食材统一采购',
            createdBy: '管理员', createdAt: '2020-11-01 09:00:00', updatedAt: '2025-11-01 10:00:00'
        },
        {
            id: 6,
            empNo: 'EMP-20260101-006',
            name: '刘店长',
            gender: 'male',
            phone: '13600136006',
            email: 'liudz@example.com',
            idCard: '310101198203203456',
            orgId: 3, orgName: '第一食堂',
            deptId: 0, deptName: '-',
            position: 'manager',
            roleIds: [1], roleNames: '超级管理员',
            hireDate: '2019-07-01',
            status: 'inactive',
            remark: '已离职，账号已禁用',
            createdBy: '管理员', createdAt: '2019-07-01 09:00:00', updatedAt: '2026-01-01 08:00:00'
        }
    ],

    /* ===================== 设备管理（equipment）===================== */
    equipment: [
        {
            id: 1,
            equipNo: 'EQ-20260101-001',
            equipName: '六眼燃气灶',
            equipType: 'stove',
            orgId: 1, orgName: '第一食堂',
            area: 'hot',
            brand: '麦科',
            model: 'MK-6B',
            supplierId: 1, supplierName: '绿色农产品供应商',
            purchaseDate: '2025-06-15',
            status: 'normal',
            usageHours: 1280,
            lastMaintainDate: '2026-01-10',
            nextMaintainDate: '2026-07-10',
            maintainCycle: 180,
            maintainRecord: '更换燃气阀门，检查火焰均匀性',
            remark: '主灶台，日均使用约8小时',
            createdBy: '管理员', createdAt: '2025-06-16 09:00:00', updatedAt: '2026-01-10 15:00:00'
        },
        {
            id: 2,
            equipNo: 'EQ-20260101-002',
            equipName: '万能蒸烤箱',
            equipType: 'steamoven',
            orgId: 1, orgName: '第一食堂',
            area: 'hot',
            brand: '爱科',
            model: 'AK-S60',
            supplierId: 2, supplierName: '鲜源肉类供应商',
            purchaseDate: '2025-08-20',
            status: 'normal',
            usageHours: 860,
            lastMaintainDate: '2026-02-01',
            nextMaintainDate: '2026-08-01',
            maintainCycle: 180,
            maintainRecord: '清洗蒸汽管道，校准温控传感器',
            remark: '用于蒸鱼、蒸饭等',
            createdBy: '管理员', createdAt: '2025-08-21 10:00:00', updatedAt: '2026-02-01 11:00:00'
        },
        {
            id: 3,
            equipNo: 'EQ-20260101-003',
            equipName: '立式冷冻冰柜',
            equipType: 'freezer',
            orgId: 2, orgName: '第二食堂',
            area: 'storage',
            brand: '海尔',
            model: 'BD-568',
            supplierId: 3, supplierName: '大洋水产供应商',
            purchaseDate: '2025-03-10',
            status: 'normal',
            usageHours: 3650,
            lastMaintainDate: '2026-01-20',
            nextMaintainDate: '2026-04-20',
            maintainCycle: 90,
            maintainRecord: '清洗蒸发器，补充制冷剂',
            remark: '存放冻品食材，温度设定-18℃',
            createdBy: '管理员', createdAt: '2025-03-11 08:00:00', updatedAt: '2026-01-20 14:00:00'
        },
        {
            id: 4,
            equipNo: 'EQ-20260101-004',
            equipName: '高温消毒柜',
            equipType: 'sterilizer',
            orgId: 2, orgName: '第二食堂',
            area: 'cold',
            brand: '康星',
            model: 'KX-200',
            supplierId: 1, supplierName: '绿色农产品供应商',
            purchaseDate: '2024-11-05',
            status: 'repair',
            usageHours: 2100,
            lastMaintainDate: '2025-12-01',
            nextMaintainDate: '2026-03-01',
            maintainCycle: 90,
            maintainRecord: '加热管损坏待更换',
            remark: '目前停用中，等待配件',
            createdBy: '管理员', createdAt: '2024-11-06 09:00:00', updatedAt: '2026-03-01 09:00:00'
        },
        {
            id: 5,
            equipNo: 'EQ-20260101-005',
            equipName: '旋转烤箱',
            equipType: 'oven',
            orgId: 3, orgName: '第三食堂',
            area: 'hot',
            brand: '法迪欧',
            model: 'FDO-R18',
            supplierId: 2, supplierName: '鲜源肉类供应商',
            purchaseDate: '2025-09-01',
            status: 'normal',
            usageHours: 720,
            lastMaintainDate: '2026-02-15',
            nextMaintainDate: '2026-08-15',
            maintainCycle: 180,
            maintainRecord: '清洁内胆，校准定时器',
            remark: '用于烘焙面包、烤肉',
            createdBy: '管理员', createdAt: '2025-09-02 08:30:00', updatedAt: '2026-02-15 16:00:00'
        },
        {
            id: 6,
            equipNo: 'EQ-20260101-006',
            equipName: '商用洗碗机',
            equipType: 'other',
            orgId: 3, orgName: '第三食堂',
            area: 'cold',
            brand: '霍巴特',
            model: 'AMXX-180',
            supplierId: 3, supplierName: '大洋水产供应商',
            purchaseDate: '2024-07-18',
            status: 'inactive',
            usageHours: 4200,
            lastMaintainDate: '2025-11-10',
            nextMaintainDate: '2026-02-10',
            maintainCycle: 90,
            maintainRecord: '喷嘴堵塞，需更换水泵',
            remark: '已停用，计划采购新设备替换',
            createdBy: '管理员', createdAt: '2024-07-19 10:00:00', updatedAt: '2025-11-10 14:00:00'
        }
    ],

    /* ===================== 留样任务（sample_tasks）===================== */
    sampleTasks: [
        {
            id: 1,
            sampleNo: 'SP-20260322-001',
            cookTaskId: 5, cookTaskNo: 'CT-20260322-001',
            dishName: '番茄蛋花汤',
            sampleWeight: 125,
            sampleTime: '2026-03-22 08:10:00',
            samplerId: 2, samplerName: '钱仓管',
            storageLocation: '留样柜A-01 4℃',
            qualityScore: 92,
            status: 'disposed',   /* pending / disposed / expired */
            photoUrl: '',
            remark: '颜色正常，汤色清亮',
            expireTime: '2026-03-24 08:10:00',
            tenantId: 1,
            createdAt: '2026-03-22 08:10:00', updatedAt: '2026-03-24 09:00:00'
        },
        {
            id: 2,
            sampleNo: 'SP-20260322-002',
            cookTaskId: 4, cookTaskNo: 'CT-20260322-004',
            dishName: '白米饭',
            sampleWeight: 125,
            sampleTime: '2026-03-22 11:45:00',
            samplerId: 3, samplerName: '孙大厨',
            storageLocation: '留样柜A-02 4℃',
            qualityScore: null,
            status: 'pending',
            photoUrl: '',
            remark: '',
            expireTime: '2026-03-24 11:45:00',
            tenantId: 1,
            createdAt: '2026-03-22 11:45:00', updatedAt: '2026-03-22 11:45:00'
        },
        {
            id: 3,
            sampleNo: 'SP-20260323-001',
            cookTaskId: 3, cookTaskNo: 'CT-20260323-003',
            dishName: '清蒸鱼',
            sampleWeight: 125,
            sampleTime: '2026-03-23 12:05:00',
            samplerId: 1, samplerName: '赵厨长',
            storageLocation: '留样柜B-01 4℃',
            qualityScore: null,
            status: 'expired',
            photoUrl: '',
            remark: '鱼肉新鲜，已过期自动标记',
            expireTime: '2026-03-23 12:05:00',
            tenantId: 1,
            createdAt: '2026-03-23 12:05:00', updatedAt: '2026-03-24 00:00:00'
        },
        {
            id: 4,
            sampleNo: 'SP-20260324-001',
            cookTaskId: 1, cookTaskNo: 'CT-20260324-001',
            dishName: '红烧肉',
            sampleWeight: 125,
            sampleTime: '2026-03-24 12:40:00',
            samplerId: 1, samplerName: '赵厨长',
            storageLocation: '留样柜A-01 4℃',
            qualityScore: null,
            status: 'pending',
            photoUrl: '',
            remark: '',
            expireTime: '2026-03-26 12:40:00',
            tenantId: 1,
            createdAt: '2026-03-24 12:40:00', updatedAt: '2026-03-24 12:40:00'
        },
        {
            id: 5,
            sampleNo: 'SP-20260324-002',
            cookTaskId: 2, cookTaskNo: 'CT-20260324-002',
            dishName: '清炒西兰花',
            sampleWeight: 125,
            sampleTime: '2026-03-24 12:45:00',
            samplerId: 4, samplerName: '李助厨',
            storageLocation: '留样柜A-03 4℃',
            qualityScore: null,
            status: 'pending',
            photoUrl: '',
            remark: '蔬菜新鲜',
            expireTime: '2026-03-26 12:45:00',
            tenantId: 1,
            createdAt: '2026-03-24 12:45:00', updatedAt: '2026-03-24 12:45:00'
        }
    ],

    /* ===================== 销样任务（dispose_tasks）===================== */
    disposeTasks: [
        {
            id: 1,
            disposeNo: 'DS-20260324-001',
            sampleId: 1, sampleNo: 'SP-20260322-001',
            dishName: '番茄蛋花汤',
            disposeTime: '2026-03-24 09:00:00',
            disposerId: 2, disposerName: '钱仓管',
            disposeReason: 'normal',   /* normal / expired / abnormal */
            status: 'done',
            remark: '正常留样周期结束，销样处理',
            tenantId: 1,
            createdAt: '2026-03-24 09:00:00', updatedAt: '2026-03-24 09:00:00'
        }
    ],

    /* ===================== 留样柜列表（Mock）===================== */
    sampleCabinets: [
        { id: 1, name: '留样柜A-01 4℃' },
        { id: 2, name: '留样柜A-02 4℃' },
        { id: 3, name: '留样柜A-03 4℃' },
        { id: 4, name: '留样柜B-01 4℃' },
        { id: 5, name: '留样柜B-02 4℃' }
    ],

    /* ===================== 晨检任务（health_check_record）===================== */
    healthCheckTasks: [
        {
            id: 1,
            checkNo: 'HC-20260323-001',
            employeeId: 1, employeeNo: 'EMP-20260101-001', employeeName: '赵厨长',
            orgId: 3, orgName: '第一食堂', deptId: 4, deptName: '后厨管理部',
            position: 'chef',
            checkDate: '2026-03-23',
            checkTimeStart: '2026-03-23 06:00:00', checkTimeEnd: '2026-03-23 09:00:00',
            faceVerifyStatus: 'verified', faceImageUrl: '', faceMatchScore: 96.5,
            faceVerifyTime: '2026-03-23 07:12:00',
            temperature: 36.5, hasFever: false, hasCough: false, hasSkinDisease: false,
            handHygiene: 'pass', uniformCheck: 'pass',
            checkResult: 'pass', status: 'done',
            checkerId: 1, checkerName: '赵厨长', checkFinishTime: '2026-03-23 07:15:00',
            remark: '', tenantId: 1,
            createdAt: '2026-03-23 07:00:00', updatedAt: '2026-03-23 07:15:00'
        },
        {
            id: 2,
            checkNo: 'HC-20260323-002',
            employeeId: 3, employeeNo: 'EMP-20260101-003', employeeName: '孙大厨',
            orgId: 3, orgName: '第一食堂', deptId: 4, deptName: '后厨管理部',
            position: 'cookworker',
            checkDate: '2026-03-23',
            checkTimeStart: '2026-03-23 06:00:00', checkTimeEnd: '2026-03-23 09:00:00',
            faceVerifyStatus: 'verified', faceImageUrl: '', faceMatchScore: 91.2,
            faceVerifyTime: '2026-03-23 07:20:00',
            temperature: 36.7, hasFever: false, hasCough: false, hasSkinDisease: false,
            handHygiene: 'pass', uniformCheck: 'pass',
            checkResult: 'pass', status: 'done',
            checkerId: 1, checkerName: '赵厨长', checkFinishTime: '2026-03-23 07:22:00',
            remark: '', tenantId: 1,
            createdAt: '2026-03-23 07:00:00', updatedAt: '2026-03-23 07:22:00'
        },
        {
            id: 3,
            checkNo: 'HC-20260323-003',
            employeeId: 4, employeeNo: 'EMP-20260101-004', employeeName: '李助厨',
            orgId: 3, orgName: '第一食堂', deptId: 4, deptName: '后厨管理部',
            position: 'cookworker',
            checkDate: '2026-03-23',
            checkTimeStart: '2026-03-23 06:00:00', checkTimeEnd: '2026-03-23 09:00:00',
            faceVerifyStatus: 'unverified', faceImageUrl: '', faceMatchScore: null,
            faceVerifyTime: null,
            temperature: null, hasFever: false, hasCough: false, hasSkinDisease: false,
            handHygiene: null, uniformCheck: null,
            checkResult: 'unfinished', status: 'overdue',
            checkerId: null, checkerName: null, checkFinishTime: null,
            remark: '超时未完成晨检', tenantId: 1,
            createdAt: '2026-03-23 07:00:00', updatedAt: '2026-03-24 00:00:00'
        },
        {
            id: 4,
            checkNo: 'HC-20260324-001',
            employeeId: 1, employeeNo: 'EMP-20260101-001', employeeName: '赵厨长',
            orgId: 3, orgName: '第一食堂', deptId: 4, deptName: '后厨管理部',
            position: 'chef',
            checkDate: '2026-03-24',
            checkTimeStart: '2026-03-24 06:00:00', checkTimeEnd: '2026-03-24 09:00:00',
            faceVerifyStatus: 'unverified', faceImageUrl: '', faceMatchScore: null,
            faceVerifyTime: null,
            temperature: null, hasFever: false, hasCough: false, hasSkinDisease: false,
            handHygiene: null, uniformCheck: null,
            checkResult: 'unfinished', status: 'pending',
            checkerId: null, checkerName: null, checkFinishTime: null,
            remark: '', tenantId: 1,
            createdAt: '2026-03-24 06:00:00', updatedAt: '2026-03-24 06:00:00'
        },
        {
            id: 5,
            checkNo: 'HC-20260324-002',
            employeeId: 3, employeeNo: 'EMP-20260101-003', employeeName: '孙大厨',
            orgId: 3, orgName: '第一食堂', deptId: 4, deptName: '后厨管理部',
            position: 'cookworker',
            checkDate: '2026-03-24',
            checkTimeStart: '2026-03-24 06:00:00', checkTimeEnd: '2026-03-24 09:00:00',
            faceVerifyStatus: 'unverified', faceImageUrl: '', faceMatchScore: null,
            faceVerifyTime: null,
            temperature: null, hasFever: false, hasCough: false, hasSkinDisease: false,
            handHygiene: null, uniformCheck: null,
            checkResult: 'unfinished', status: 'pending',
            checkerId: null, checkerName: null, checkFinishTime: null,
            remark: '', tenantId: 1,
            createdAt: '2026-03-24 06:00:00', updatedAt: '2026-03-24 06:00:00'
        },
        {
            id: 6,
            checkNo: 'HC-20260324-003',
            employeeId: 4, employeeNo: 'EMP-20260101-004', employeeName: '李助厨',
            orgId: 3, orgName: '第一食堂', deptId: 4, deptName: '后厨管理部',
            position: 'cookworker',
            checkDate: '2026-03-24',
            checkTimeStart: '2026-03-24 06:00:00', checkTimeEnd: '2026-03-24 09:00:00',
            faceVerifyStatus: 'unverified', faceImageUrl: '', faceMatchScore: null,
            faceVerifyTime: null,
            temperature: null, hasFever: false, hasCough: false, hasSkinDisease: false,
            handHygiene: null, uniformCheck: null,
            checkResult: 'unfinished', status: 'pending',
            checkerId: null, checkerName: null, checkFinishTime: null,
            remark: '', tenantId: 1,
            createdAt: '2026-03-24 06:00:00', updatedAt: '2026-03-24 06:00:00'
        }
    ],

    /* ===================== 角色分组（role_groups）===================== */
    roleGroups: [
        { id: 1, groupName: '系统管理组', sort: 1, remark: '负责系统配置与权限管理', createdAt: '2026-01-01 08:00:00' },
        { id: 2, groupName: '采购管理组', sort: 2, remark: '负责采购相关业务操作',   createdAt: '2026-01-01 08:00:00' },
        { id: 3, groupName: '仓储管理组', sort: 3, remark: '负责仓库、库存、出入库', createdAt: '2026-01-01 08:00:00' },
        { id: 4, groupName: '厨房管理组', sort: 4, remark: '负责菜谱计划与烹饪管理', createdAt: '2026-01-01 08:00:00' }
    ],

    /* ===================== 角色（roles）===================== */
    roles: [
        {
            id: 1,
            roleName: '超级管理员',
            roleCode: 'SUPER_ADMIN',
            groupId: 1, groupName: '系统管理组',
            status: 'active',
            remark: '拥有全部功能权限和数据权限',
            dataScope: 'all',        // all | org | dept
            dataScopeOrgIds: [],
            funcPermissions: ['dashboard','supplier','purchasePlan','purchase','warehouse','material','inventory','inbound','outbound','stocktake','recipe','plan','cook','sample','org','employee','rolePermission'],
            createdAt: '2026-01-01 09:00:00'
        },
        {
            id: 2,
            roleName: '采购专员',
            roleCode: 'PURCHASER',
            groupId: 2, groupName: '采购管理组',
            status: 'active',
            remark: '可操作采购计划与采购订单',
            dataScope: 'org',
            dataScopeOrgIds: [1, 2],
            funcPermissions: ['dashboard','supplier','purchasePlan','purchase'],
            createdAt: '2026-01-05 09:00:00'
        },
        {
            id: 3,
            roleName: '仓管员',
            roleCode: 'WAREHOUSE_KEEPER',
            groupId: 3, groupName: '仓储管理组',
            status: 'active',
            remark: '可操作仓库、物料、出入库、盘点',
            dataScope: 'dept',
            dataScopeOrgIds: [1],
            funcPermissions: ['dashboard','warehouse','material','inventory','inbound','outbound','stocktake'],
            createdAt: '2026-01-06 09:00:00'
        },
        {
            id: 4,
            roleName: '厨师长',
            roleCode: 'CHEF_LEAD',
            groupId: 4, groupName: '厨房管理组',
            status: 'active',
            remark: '可操作菜谱库、菜谱计划、烹饪记录',
            dataScope: 'org',
            dataScopeOrgIds: [3],
            funcPermissions: ['dashboard','recipe','plan','cook','sample'],
            createdAt: '2026-01-07 09:00:00'
        },
        {
            id: 5,
            roleName: '只读审计员',
            roleCode: 'AUDITOR',
            groupId: 1, groupName: '系统管理组',
            status: 'inactive',
            remark: '仅可查看所有模块数据，无操作权限',
            dataScope: 'all',
            dataScopeOrgIds: [],
            funcPermissions: ['dashboard','inventory','inbound','outbound','stocktake','purchase'],
            createdAt: '2026-02-01 09:00:00'
        }
    ],

    /* ===================== 用餐评价（meal_reviews）===================== */
    mealReviews: [
        {
            id: 1, reviewNo: 'RV-20260322-001',
            employeeId: 1, employeeName: '赵厨长',
            menuId: 1, menuName: '番茄蛋花汤',
            reviewDate: '2026-03-22', mealType: 'breakfast',
            overallScore: 5, tasteScore: 5, nutritionScore: 4, portionScore: 5,
            content: '汤色清亮，口感鲜美，非常满意！',
            isComplaint: false, complaintType: null,
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-22 08:30:00', updatedAt: '2026-03-22 08:30:00'
        },
        {
            id: 2, reviewNo: 'RV-20260322-002',
            employeeId: 3, employeeName: '孙大厨',
            menuId: 2, menuName: '红烧肉',
            reviewDate: '2026-03-22', mealType: 'lunch',
            overallScore: 2, tasteScore: 2, nutritionScore: 3, portionScore: 2,
            content: '肉质偏老，口味偏咸，份量也较少，希望改进。',
            isComplaint: true, complaintType: 'dish_quality',
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-22 12:50:00', updatedAt: '2026-03-22 12:50:00'
        },
        {
            id: 3, reviewNo: 'RV-20260323-001',
            employeeId: 4, employeeName: '李助厨',
            menuId: 3, menuName: '清蒸鱼',
            reviewDate: '2026-03-23', mealType: 'lunch',
            overallScore: 1, tasteScore: 1, nutritionScore: 2, portionScore: 2,
            content: '鱼有腥味，明显不新鲜，存在食品安全隐患！',
            isComplaint: true, complaintType: 'food_safety',
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-23 13:05:00', updatedAt: '2026-03-23 13:05:00'
        },
        {
            id: 4, reviewNo: 'RV-20260323-002',
            employeeId: 2, employeeName: '钱仓管',
            menuId: 4, menuName: '清炒西兰花',
            reviewDate: '2026-03-23', mealType: 'lunch',
            overallScore: 4, tasteScore: 4, nutritionScore: 5, portionScore: 4,
            content: '蔬菜新鲜，炒制火候恰当，营养丰富。',
            isComplaint: false, complaintType: null,
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-23 13:20:00', updatedAt: '2026-03-23 13:20:00'
        },
        {
            id: 5, reviewNo: 'RV-20260324-001',
            employeeId: 5, employeeName: '王采购',
            menuId: 5, menuName: '白米饭',
            reviewDate: '2026-03-24', mealType: 'lunch',
            overallScore: 2, tasteScore: 2, nutritionScore: 3, portionScore: 3,
            content: '服务员态度恶劣，打饭时直接摔碗，服务体验极差。',
            isComplaint: true, complaintType: 'service',
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-24 12:40:00', updatedAt: '2026-03-24 12:40:00'
        }
    ],

    /* ===================== 监管申诉与反馈（supervision_appeals）===================== */
    supervisionAppeals: [
        {
            id: 1, appealNo: 'AP-20260321-001',
            subject: '后厨卫生环境不达标',
            appealUnit: '华东区监管办',
            contactName: '监管员李明', contactPhone: '13600136001',
            appealTime: '2026-03-21 10:00:00',
            content: '现场巡查发现第一食堂后厨地面油污堆积，墙壁有霉斑，不符合食品安全标准，请立即整改。',
            appealType: 'complaint',
            status: 'pending',
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-21 10:00:00', updatedAt: '2026-03-21 10:00:00'
        },
        {
            id: 2, appealNo: 'AP-20260322-001',
            subject: '食材溯源记录缺失',
            appealUnit: '区食品监管局',
            contactName: '检查员王刚', contactPhone: '13700137002',
            appealTime: '2026-03-22 09:30:00',
            content: '抽查发现近7日内猪里脊食材缺少完整溯源记录，供应商资质证明文件不全，存在合规风险。',
            appealType: 'complaint',
            status: 'handling',
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-22 09:30:00', updatedAt: '2026-03-23 11:00:00'
        },
        {
            id: 3, appealNo: 'AP-20260323-001',
            subject: '菜品营养标识建议',
            appealUnit: '营养健康促进协会',
            contactName: '专员赵丽', contactPhone: '13500135003',
            appealTime: '2026-03-23 14:00:00',
            content: '建议在菜单上增加营养成分标注，帮助就餐人员做出更健康的选择，符合健康饮食推广要求。',
            appealType: 'suggestion',
            status: 'done',
            orgId: 3, orgName: '第一食堂',
            tenantId: 1, createdAt: '2026-03-23 14:00:00', updatedAt: '2026-03-24 09:00:00'
        }
    ],

    /* ===================== 投诉归集（complaints）===================== */
    complaints: [
        {
            id: 1, complaintNo: 'CP-20260322-001',
            source: 'review', sourceRefNo: 'RV-20260322-002', sourceRefId: 2,
            complaintType: 'dish_quality',
            complainantName: '孙大厨', complainantPhone: '15000150003',
            complaintTime: '2026-03-22 12:50:00',
            content: '肉质偏老，口味偏咸，份量也较少，希望改进。',
            orgId: 3, orgName: '第一食堂',
            status: 'closed',
            /* 派单信息 */
            assignerId: 1, assignerName: '赵厨长',
            assignTime: '2026-03-22 14:00:00',
            handlerId: 1, handlerName: '赵厨长',
            deadline: '2026-03-23 14:00:00',
            assignRemark: '请尽快核查并改进菜品质量',
            /* 处理信息 */
            handleContent: '已与后厨负责人沟通，调整了红烧肉的烹饪时间和调味比例，确保口感和份量达标。',
            handleResult: 'resolved',
            handleTime: '2026-03-23 10:00:00',
            /* 闭环信息 */
            reviewerId: 1, reviewerName: '赵厨长',
            reviewTime: '2026-03-23 11:00:00',
            reviewOpinion: '处理方案合理，菜品质量已整改，同意闭环。',
            closeTime: '2026-03-23 11:00:00',
            tenantId: 1, createdAt: '2026-03-22 12:50:00', updatedAt: '2026-03-23 11:00:00'
        },
        {
            id: 2, complaintNo: 'CP-20260323-001',
            source: 'review', sourceRefNo: 'RV-20260323-001', sourceRefId: 3,
            complaintType: 'food_safety',
            complainantName: '李助厨', complainantPhone: '18600186004',
            complaintTime: '2026-03-23 13:05:00',
            content: '鱼有腥味，明显不新鲜，存在食品安全隐患！',
            orgId: 3, orgName: '第一食堂',
            status: 'handling',
            assignerId: 1, assignerName: '赵厨长',
            assignTime: '2026-03-23 14:30:00',
            handlerId: 3, handlerName: '孙大厨',
            deadline: '2026-03-24 14:30:00',
            assignRemark: '食安问题优先处理，请严查食材新鲜度',
            handleContent: null, handleResult: null, handleTime: null,
            reviewerId: null, reviewerName: null, reviewTime: null,
            reviewOpinion: null, closeTime: null,
            tenantId: 1, createdAt: '2026-03-23 13:05:00', updatedAt: '2026-03-23 14:30:00'
        },
        {
            id: 3, complaintNo: 'CP-20260321-001',
            source: 'appeal', sourceRefNo: 'AP-20260321-001', sourceRefId: 1,
            complaintType: 'environment',
            complainantName: '监管员李明', complainantPhone: '13600136001',
            complaintTime: '2026-03-21 10:00:00',
            content: '现场巡查发现第一食堂后厨地面油污堆积，墙壁有霉斑，不符合食品安全标准，请立即整改。',
            orgId: 3, orgName: '第一食堂',
            status: 'reviewing',
            assignerId: 1, assignerName: '赵厨长',
            assignTime: '2026-03-21 11:00:00',
            handlerId: 1, handlerName: '赵厨长',
            deadline: '2026-03-22 11:00:00',
            assignRemark: '环境问题立即整改',
            handleContent: '已组织后厨全面大扫除，清理油污，墙面消毒处理，整改完成。',
            handleResult: 'resolved',
            handleTime: '2026-03-21 17:00:00',
            reviewerId: null, reviewerName: null, reviewTime: null,
            reviewOpinion: null, closeTime: null,
            tenantId: 1, createdAt: '2026-03-21 10:00:00', updatedAt: '2026-03-21 17:00:00'
        },
        {
            id: 4, complaintNo: 'CP-20260324-001',
            source: 'review', sourceRefNo: 'RV-20260324-001', sourceRefId: 5,
            complaintType: 'service',
            complainantName: '王采购', complainantPhone: '17700177005',
            complaintTime: '2026-03-24 12:40:00',
            content: '服务员态度恶劣，打饭时直接摔碗，服务体验极差。',
            orgId: 3, orgName: '第一食堂',
            status: 'pending',
            assignerId: null, assignerName: null,
            assignTime: null, handlerId: null, handlerName: null,
            deadline: null, assignRemark: null,
            handleContent: null, handleResult: null, handleTime: null,
            reviewerId: null, reviewerName: null, reviewTime: null,
            reviewOpinion: null, closeTime: null,
            tenantId: 1, createdAt: '2026-03-24 12:40:00', updatedAt: '2026-03-24 12:40:00'
        },
        {
            id: 5, complaintNo: 'CP-20260322-002',
            source: 'manual',  sourceRefNo: null, sourceRefId: null,
            complaintType: 'food_safety',
            complainantName: '匿名用户', complainantPhone: '—',
            complaintTime: '2026-03-22 18:00:00',
            content: '发现厨房人员操作时未戴手套，存在食品安全风险。',
            orgId: 3, orgName: '第一食堂',
            status: 'rejected',
            assignerId: 1, assignerName: '赵厨长',
            assignTime: '2026-03-22 19:00:00',
            handlerId: 2, handlerName: '钱仓管',
            deadline: '2026-03-23 19:00:00',
            assignRemark: '核实处理',
            handleContent: '核查视频记录，当日厨房人员均按规范佩戴手套，投诉内容不实。',
            handleResult: 'unresolved',
            handleTime: '2026-03-23 10:00:00',
            reviewerId: 1, reviewerName: '赵厨长',
            reviewTime: '2026-03-23 11:30:00',
            reviewOpinion: '经核查投诉内容与事实不符，驳回本次投诉。',
            closeTime: null, rejectReason: '投诉内容与监控记录不符，经审核驳回',
            tenantId: 1, createdAt: '2026-03-22 18:00:00', updatedAt: '2026-03-23 11:30:00'
        }
    ],

    /* ===================== 评价与投诉单据（eva_complaints）===================== */
    evaComplaints: [
        {
            id: 1, docNo: 'EVA-20260322-001', docType: 'review',
            source: 'meal', title: '番茄蛋花汤', menuName: '番茄蛋花汤',
            contactName: '赵厨长', contactPhone: '13800138001',
            orgId: 3, orgName: '第一食堂',
            overallScore: 5, content: '汤色清亮，口感鲜美，非常满意！',
            priority: 'low',
            handleStatus: 'closed', assignType: 'auto',
            assignerId: 1, assignerName: '赵厨长',
            handlerId: 1, handlerName: '赵厨长',
            assignTime: '2026-03-22 08:35:00',
            deadline: '2026-03-23 08:35:00',
            assignRemark: '好评无需派单，自动归档',
            handleContent: '感谢好评，持续保持品质。', handleResult: 'resolved',
            handleTime: '2026-03-22 09:00:00', closeTime: '2026-03-22 09:00:00',
            logs: [
                { time: '2026-03-22 08:30:00', op: '用户提交用餐评价（5星）' },
                { time: '2026-03-22 08:35:00', op: '系统自动归档，无需处理' },
                { time: '2026-03-22 09:00:00', op: '状态更新为已闭环' }
            ],
            tenantId: 1, createdAt: '2026-03-22 08:30:00', updatedAt: '2026-03-22 09:00:00'
        },
        {
            id: 2, docNo: 'COM-20260322-001', docType: 'complaint',
            source: 'meal', title: '红烧肉菜品质量投诉', menuName: '红烧肉',
            contactName: '孙大厨', contactPhone: '15000150003',
            orgId: 3, orgName: '第一食堂',
            overallScore: 2, content: '肉质偏老，口味偏咸，份量也较少，希望改进。',
            priority: 'high',
            handleStatus: 'closed', assignType: 'auto',
            assignerId: 1, assignerName: '赵厨长',
            handlerId: 1, handlerName: '赵厨长',
            assignTime: '2026-03-22 13:00:00',
            deadline: '2026-03-23 13:00:00',
            assignRemark: '菜品质量投诉，自动派单至厨师长',
            handleContent: '已与后厨负责人沟通，调整红烧肉烹饪工艺，确保口感和份量达标。',
            handleResult: 'resolved', handleTime: '2026-03-23 10:00:00',
            closeTime: '2026-03-23 11:00:00',
            logs: [
                { time: '2026-03-22 12:50:00', op: '用户提交投诉（2星）' },
                { time: '2026-03-22 13:00:00', op: '系统自动派单至赵厨长（菜品质量）' },
                { time: '2026-03-23 10:00:00', op: '赵厨长提交处理结果：已解决' },
                { time: '2026-03-23 11:00:00', op: '审核通过，状态更新为已闭环' }
            ],
            tenantId: 1, createdAt: '2026-03-22 12:50:00', updatedAt: '2026-03-23 11:00:00'
        },
        {
            id: 3, docNo: 'COM-20260323-001', docType: 'complaint',
            source: 'meal', title: '清蒸鱼食品安全投诉', menuName: '清蒸鱼',
            contactName: '李助厨', contactPhone: '18600186004',
            orgId: 3, orgName: '第一食堂',
            overallScore: 1, content: '鱼有腥味，明显不新鲜，存在食品安全隐患！',
            priority: 'high',
            handleStatus: 'assigned', assignType: 'auto',
            assignerId: 1, assignerName: '赵厨长',
            handlerId: 3, handlerName: '孙大厨',
            assignTime: '2026-03-23 13:30:00',
            deadline: '2026-03-24 13:30:00',
            assignRemark: '食安问题高优先级，自动派单至大厨核查',
            handleContent: null, handleResult: null, handleTime: null, closeTime: null,
            logs: [
                { time: '2026-03-23 13:05:00', op: '用户提交投诉（1星，食品安全）' },
                { time: '2026-03-23 13:30:00', op: '系统自动派单至孙大厨（食安问题）' }
            ],
            tenantId: 1, createdAt: '2026-03-23 13:05:00', updatedAt: '2026-03-23 13:30:00'
        },
        {
            id: 4, docNo: 'COM-20260321-001', docType: 'complaint',
            source: 'supervision', title: '后厨卫生环境不达标', menuName: null,
            contactName: '监管员李明', contactPhone: '13600136001',
            orgId: 3, orgName: '第一食堂',
            overallScore: null, content: '现场巡查发现后厨地面油污堆积，墙壁有霉斑，不符合食品安全标准，请立即整改。',
            priority: 'high',
            handleStatus: 'assigned', assignType: 'manual',
            assignerId: 1, assignerName: '赵厨长',
            handlerId: 1, handlerName: '赵厨长',
            assignTime: '2026-03-21 11:00:00',
            deadline: '2026-03-22 11:00:00',
            assignRemark: '监管投诉，人工指定负责人处理',
            handleContent: null, handleResult: null, handleTime: null, closeTime: null,
            logs: [
                { time: '2026-03-21 10:00:00', op: '监管单位提交申诉投诉' },
                { time: '2026-03-21 11:00:00', op: '管理员人工派单至赵厨长（环境整改）' }
            ],
            tenantId: 1, createdAt: '2026-03-21 10:00:00', updatedAt: '2026-03-21 11:00:00'
        },
        {
            id: 5, docNo: 'COM-20260324-001', docType: 'complaint',
            source: 'meal', title: '服务态度投诉', menuName: null,
            contactName: '王采购', contactPhone: '17700177005',
            orgId: 3, orgName: '第一食堂',
            overallScore: 2, content: '服务员态度恶劣，打饭时直接摔碗，服务体验极差。',
            priority: 'high',
            handleStatus: 'pending', assignType: 'none',
            assignerId: null, assignerName: null,
            handlerId: null, handlerName: null,
            assignTime: null, deadline: null, assignRemark: null,
            handleContent: null, handleResult: null, handleTime: null, closeTime: null,
            logs: [
                { time: '2026-03-24 12:40:00', op: '用户提交投诉（2星，服务态度）' }
            ],
            tenantId: 1, createdAt: '2026-03-24 12:40:00', updatedAt: '2026-03-24 12:40:00'
        },
        {
            id: 6, docNo: 'EVA-20260323-001', docType: 'review',
            source: 'meal', title: '清炒西兰花', menuName: '清炒西兰花',
            contactName: '钱仓管', contactPhone: '13900139002',
            orgId: 3, orgName: '第一食堂',
            overallScore: 4, content: '蔬菜新鲜，炒制火候恰当，营养丰富。',
            priority: 'low',
            handleStatus: 'closed', assignType: 'auto',
            assignerId: null, assignerName: null,
            handlerId: null, handlerName: null,
            assignTime: null, deadline: null, assignRemark: null,
            handleContent: null, handleResult: null, handleTime: null, closeTime: '2026-03-23 14:00:00',
            logs: [
                { time: '2026-03-23 13:20:00', op: '用户提交好评（4星）' },
                { time: '2026-03-23 14:00:00', op: '系统自动归档，无需处理' }
            ],
            tenantId: 1, createdAt: '2026-03-23 13:20:00', updatedAt: '2026-03-23 14:00:00'
        },
        {
            id: 7, docNo: 'COM-20260322-002', docType: 'complaint',
            source: 'manual', title: '厨房人员操作不规范投诉', menuName: null,
            contactName: '匿名用户', contactPhone: '—',
            orgId: 3, orgName: '第一食堂',
            overallScore: null, content: '发现厨房人员操作时未戴手套，存在食品安全风险。',
            priority: 'medium',
            handleStatus: 'pending', assignType: 'none',
            assignerId: null, assignerName: null,
            handlerId: null, handlerName: null,
            assignTime: null, deadline: null, assignRemark: null,
            handleContent: null, handleResult: null, handleTime: null, closeTime: null,
            logs: [
                { time: '2026-03-22 18:00:00', op: '人工录入投诉单' }
            ],
            tenantId: 1, createdAt: '2026-03-22 18:00:00', updatedAt: '2026-03-22 18:00:00'
        }
    ],

    /* ===================== 监控设备（cctv_cameras）===================== */
    cctvCameras: [
        {
            id: 1, cameraNo: 'CAM-20260101-001', cameraName: '切配区-01',
            zone: 'cutting', zoneName: '切配区',
            model: 'HIK-DS-2CD2T47G2', ip: '192.168.1.101',
            location: '食堂后厨切配操作台正上方', vendor: '海康威视',
            installDate: '2025-06-01', lastOnlineTime: '2026-03-24 12:50:00',
            status: 'online', fps: 25, bitrate: '4096Kbps', resolution: '1080P',
            todayViolCount: 2, totalViolCount: 15, unhandledCount: 2,
            alertTypes: ['no_mask','no_gloves','cross_contamination','raw_cooked_mix'],
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2025-06-01 09:00:00', updatedAt: '2026-03-24 12:50:00'
        },
        {
            id: 2, cameraNo: 'CAM-20260101-002', cameraName: '烹饪区-01',
            zone: 'cooking', zoneName: '烹饪区',
            model: 'HIK-DS-2CD2T47G2', ip: '192.168.1.102',
            location: '食堂后厨炒锅灶台正上方', vendor: '海康威视',
            installDate: '2025-06-01', lastOnlineTime: '2026-03-24 12:48:00',
            status: 'online', fps: 25, bitrate: '4096Kbps', resolution: '1080P',
            todayViolCount: 1, totalViolCount: 8, unhandledCount: 1,
            alertTypes: ['no_mask','fire_unattended','cross_contamination'],
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2025-06-01 09:00:00', updatedAt: '2026-03-24 12:48:00'
        },
        {
            id: 3, cameraNo: 'CAM-20260101-003', cameraName: '洗消区-01',
            zone: 'washing', zoneName: '洗消区',
            model: 'DAHUA-IPC-HDW2831T', ip: '192.168.1.103',
            location: '洗消间入口顶部', vendor: '大华技术',
            installDate: '2025-07-15', lastOnlineTime: '2026-03-24 12:45:00',
            status: 'online', fps: 20, bitrate: '2048Kbps', resolution: '1080P',
            todayViolCount: 0, totalViolCount: 3, unhandledCount: 0,
            alertTypes: ['no_handwash','no_gloves'],
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2025-07-15 10:00:00', updatedAt: '2026-03-24 12:45:00'
        },
        {
            id: 4, cameraNo: 'CAM-20260101-004', cameraName: '仓储区-01',
            zone: 'storage', zoneName: '仓储区',
            model: 'DAHUA-IPC-HDW2831T', ip: '192.168.1.104',
            location: '食材冷藏库门口顶部', vendor: '大华技术',
            installDate: '2025-07-15', lastOnlineTime: '2026-03-22 18:30:00',
            status: 'offline', fps: 0, bitrate: '—', resolution: '1080P',
            todayViolCount: 0, totalViolCount: 2, unhandledCount: 0,
            alertTypes: ['zone_violation'],
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2025-07-15 10:00:00', updatedAt: '2026-03-22 18:30:00'
        },
        {
            id: 5, cameraNo: 'CAM-20260101-005', cameraName: '烹饪区-02',
            zone: 'cooking', zoneName: '烹饪区',
            model: 'HIK-DS-2CD2T87G2', ip: '192.168.1.105',
            location: '食堂后厨蒸箱操作区顶部', vendor: '海康威视',
            installDate: '2025-09-01', lastOnlineTime: '2026-03-24 12:52:00',
            status: 'online', fps: 25, bitrate: '4096Kbps', resolution: '4K',
            todayViolCount: 0, totalViolCount: 5, unhandledCount: 0,
            alertTypes: ['no_mask','fire_unattended','zone_violation'],
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2025-09-01 08:00:00', updatedAt: '2026-03-24 12:52:00'
        },
        {
            id: 6, cameraNo: 'CAM-20260101-006', cameraName: '切配区-02',
            zone: 'cutting', zoneName: '切配区',
            model: 'HIK-DS-2CD2T47G2', ip: '192.168.1.106',
            location: '食堂后厨蔬菜切配区侧壁', vendor: '海康威视',
            installDate: '2025-09-01', lastOnlineTime: '2026-03-24 12:51:00',
            status: 'online', fps: 25, bitrate: '4096Kbps', resolution: '1080P',
            todayViolCount: 3, totalViolCount: 22, unhandledCount: 3,
            alertTypes: ['no_mask','no_gloves','raw_cooked_mix','cross_contamination'],
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2025-09-01 08:00:00', updatedAt: '2026-03-24 12:51:00'
        }
    ],

    /* ===================== 违规检测记录（cctv_violations）===================== */
    cctvViolations: [
        {
            id: 1, violNo: 'VIOL-20260324-001',
            cameraId: 6, cameraNo: 'CAM-20260101-006', cameraName: '切配区-02',
            zone: 'cutting', zoneName: '切配区',
            eventType: 'no_mask', confidence: 95,
            eventTime: '2026-03-24 09:05:12',
            status: 'unhandled', handlerId: null, handlerName: null, handleTime: null, handleRemark: null,
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-24 09:05:12'
        },
        {
            id: 2, violNo: 'VIOL-20260324-002',
            cameraId: 6, cameraNo: 'CAM-20260101-006', cameraName: '切配区-02',
            zone: 'cutting', zoneName: '切配区',
            eventType: 'raw_cooked_mix', confidence: 88,
            eventTime: '2026-03-24 10:23:45',
            status: 'unhandled', handlerId: null, handlerName: null, handleTime: null, handleRemark: null,
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-24 10:23:45'
        },
        {
            id: 3, violNo: 'VIOL-20260324-003',
            cameraId: 6, cameraNo: 'CAM-20260101-006', cameraName: '切配区-02',
            zone: 'cutting', zoneName: '切配区',
            eventType: 'no_gloves', confidence: 91,
            eventTime: '2026-03-24 11:40:08',
            status: 'unhandled', handlerId: null, handlerName: null, handleTime: null, handleRemark: null,
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-24 11:40:08'
        },
        {
            id: 4, violNo: 'VIOL-20260324-004',
            cameraId: 1, cameraNo: 'CAM-20260101-001', cameraName: '切配区-01',
            zone: 'cutting', zoneName: '切配区',
            eventType: 'cross_contamination', confidence: 82,
            eventTime: '2026-03-24 08:15:30',
            status: 'unhandled', handlerId: null, handlerName: null, handleTime: null, handleRemark: null,
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-24 08:15:30'
        },
        {
            id: 5, violNo: 'VIOL-20260324-005',
            cameraId: 1, cameraNo: 'CAM-20260101-001', cameraName: '切配区-01',
            zone: 'cutting', zoneName: '切配区',
            eventType: 'no_mask', confidence: 97,
            eventTime: '2026-03-24 09:32:17',
            status: 'unhandled', handlerId: null, handlerName: null, handleTime: null, handleRemark: null,
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-24 09:32:17'
        },
        {
            id: 6, violNo: 'VIOL-20260324-006',
            cameraId: 2, cameraNo: 'CAM-20260101-002', cameraName: '烹饪区-01',
            zone: 'cooking', zoneName: '烹饪区',
            eventType: 'fire_unattended', confidence: 99,
            eventTime: '2026-03-24 11:55:03',
            status: 'unhandled', handlerId: null, handlerName: null, handleTime: null, handleRemark: null,
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-24 11:55:03'
        },
        {
            id: 7, violNo: 'VIOL-20260323-001',
            cameraId: 1, cameraNo: 'CAM-20260101-001', cameraName: '切配区-01',
            zone: 'cutting', zoneName: '切配区',
            eventType: 'no_gloves', confidence: 93,
            eventTime: '2026-03-23 09:10:22',
            status: 'handled', handlerId: 1, handlerName: '赵厨长',
            handleTime: '2026-03-23 10:30:00', handleRemark: '已提醒员工规范佩戴手套',
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-23 09:10:22'
        },
        {
            id: 8, violNo: 'VIOL-20260323-002',
            cameraId: 5, cameraNo: 'CAM-20260101-005', cameraName: '烹饪区-02',
            zone: 'cooking', zoneName: '烹饪区',
            eventType: 'zone_violation', confidence: 87,
            eventTime: '2026-03-23 14:22:45',
            status: 'handled', handlerId: 1, handlerName: '赵厨长',
            handleTime: '2026-03-23 15:00:00', handleRemark: '已警告相关人员不得擅自进入非工作区域',
            snapUrl: '', clipUrl: '',
            orgId: 3, orgName: '第一食堂', tenantId: 1,
            createdAt: '2026-03-23 14:22:45'
        }
    ]
};
