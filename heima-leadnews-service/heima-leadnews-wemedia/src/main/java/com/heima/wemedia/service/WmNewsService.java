package com.heima.wemedia.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;
import org.springframework.web.bind.annotation.RequestBody;

public interface WmNewsService extends IService<WmNews> {

    //条件查询文章列表
    public ResponseResult findAll(WmNewsPageReqDto wmNewsPageReqDto);

    //发布/修改文章或保存为草稿
    public ResponseResult submitNews(WmNewsDto wmNewsDto);

    //文章的上下架
    public ResponseResult downOrUp(WmNewsDto wmNewsDto);

}