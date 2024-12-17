package com.heima.wemedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WmNewsMaterialMapper extends BaseMapper<WmNewsMaterial> {

     //批量保存, type表示是封面引用还是内容引用
     void saveRelations(@Param("materialIds") List<Integer> materialIds,@Param("newsId") Integer newsId, @Param("type")Short type);
}