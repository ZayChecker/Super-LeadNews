package com.heima.wemedia.controller.v1;

import com.heima.common.constants.WemediaConstants;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
public class WmNewsController {

    @Autowired
    private WmNewsService wmNewsService;

    @PostMapping("/list")
    public ResponseResult findList(@RequestBody WmNewsPageReqDto wmNewsPageReqDto){
        return wmNewsService.findAll(wmNewsPageReqDto);
    }

    @PostMapping("/submit")
    public ResponseResult submitNews(@RequestBody WmNewsDto wmNewsDto){
        return wmNewsService.submitNews(wmNewsDto);
    }

    @PostMapping("/down_or_up")
    public ResponseResult downOrUp(@RequestBody WmNewsDto wmNewsDto){
        return wmNewsService.downOrUp(wmNewsDto);
    }
}
