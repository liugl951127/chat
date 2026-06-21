package com.fin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fin.auth.entity.FinUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FinUserMapper extends BaseMapper<FinUser> {

    @Select("SELECT * FROM fin_user WHERE unionid = #{unionid} AND status = 1 LIMIT 1")
    FinUser selectByUnionid(@Param("unionid") String unionid);

    @Select("SELECT * FROM fin_user WHERE mobile_hash = #{mobileHash} AND status = 1 LIMIT 1")
    FinUser selectByMobileHash(@Param("mobileHash") String mobileHash);

    @Select("SELECT * FROM fin_user WHERE id = #{id} AND status = 1 LIMIT 1")
    FinUser selectActiveById(@Param("id") Long id);
}
