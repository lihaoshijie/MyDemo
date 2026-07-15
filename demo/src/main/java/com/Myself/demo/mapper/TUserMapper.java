package com.Myself.demo.mapper;

import com.Myself.demo.entity.TUser;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface TUserMapper {
    List<TUser> findAll();

    TUser findById(Long id);

    int insert(TUser user);

    int update(TUser user);

    int deleteById(Long id);

    List<TUser> findPage(@Param("name") String name, @Param("age") Integer age, @Param("offset") int offset, @Param("pageSize") int pageSize);

    long count(@Param("name") String name, @Param("age") Integer age);
}
