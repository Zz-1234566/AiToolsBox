package com.example.aitools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aitools.entity.UserFavorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 幂等收藏：已存在（含被逻辑删除的行）则恢复 dr=0，否则插入。
     * <p>解决「逻辑删除 + 唯一索引 uk_user_target」冲突：
     * 取消收藏只把 dr 置 1，行仍在，直接 INSERT 会撞唯一键。</p>
     */
    @Insert("INSERT INTO sys_user_favorite (user_id, target_type, target_id, dr) "
            + "VALUES (#{userId}, #{targetType}, #{targetId}, 0) "
            + "ON DUPLICATE KEY UPDATE dr = 0")
    int upsertFavorite(@Param("userId") Long userId,
                       @Param("targetType") String targetType,
                       @Param("targetId") String targetId);
}
