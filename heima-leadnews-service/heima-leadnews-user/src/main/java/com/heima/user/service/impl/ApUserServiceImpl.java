package com.heima.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.ApUserMapper;
import com.heima.user.service.ApUserService;
import com.heima.utils.common.AppJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ApUserServiceImpl extends ServiceImpl<ApUserMapper, ApUser> implements ApUserService {
    @Override
    public ResponseResult login(LoginDto loginDto) {
        //1.正常登录（手机号+密码登录）
        if(!StringUtils.isBlank(loginDto.getPhone()) && !StringUtils.isBlank(loginDto.getPassword())){
            ApUser apUser = lambdaQuery()
                    .eq(ApUser::getPhone, loginDto.getPhone())
                    .one();
            if(apUser == null){
                return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "用户不存在");
            }

            //比对密码
            String salt = apUser.getSalt();
            String pswd = loginDto.getPassword();
            pswd = DigestUtils.md5DigestAsHex((pswd + salt).getBytes());
            if(!pswd.equals(apUser.getPassword())){
                return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
            }

            //返回数据，jwt
            Map<String, Object> map = new HashMap<>();
            map.put("token", AppJwtUtil.getToken(apUser.getId().longValue()));
            //有些值不能返回给前端
            apUser.setSalt("");
            apUser.setPassword("");
            map.put("user", apUser);
            return ResponseResult.okResult(map);
        }
        else{
            //2.游客，同样返回token，id=0
            Map<String, Object> map = new HashMap<>();
            map.put("token", AppJwtUtil.getToken(0L));
            return ResponseResult.okResult(map);
        }
    }
}
