package com.xykj.sys.service;

import com.xykj.sys.dto.RoleGroupCreateDTO;
import com.xykj.sys.dto.RoleGroupUpdateDTO;
import com.xykj.sys.vo.RoleGroupVO;

import java.util.List;

/**
 * 角色分组服务接口
 */
public interface RoleGroupService {

    /**
     * 获取分组列表
     *
     * @return 分组列表
     */
    List<RoleGroupVO> list();

    /**
     * 获取分组详情
     *
     * @param id 分组ID
     * @return 分组详情
     */
    RoleGroupVO getDetail(Long id);

    /**
     * 新增分组
     *
     * @param dto 创建DTO
     * @return 分组ID
     */
    Long create(RoleGroupCreateDTO dto);

    /**
     * 更新分组
     *
     * @param dto 更新DTO
     */
    void update(RoleGroupUpdateDTO dto);

    /**
     * 删除分组
     *
     * @param id 分组ID
     */
    void delete(Long id);
}
