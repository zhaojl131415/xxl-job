package com.xxl.job.admin.controller;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.service.impl.AdminBizImpl;
import com.xxl.job.core.biz.AdminBiz;
import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.biz.model.RegistryParam;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.GsonTool;
import com.xxl.job.core.util.XxlJobRemotingUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by xuxueli on 17/5/10.
 */
@Controller
@RequestMapping("/api")
public class JobApiController {

    @Resource
    private AdminBiz adminBiz;

    /**
     * api
     *
     * @param uri
     * @param data
     * @return
     */
    @RequestMapping("/{uri}")
    @ResponseBody
    @PermissionLimit(limit=false)
    public ReturnT<String> api(HttpServletRequest request, @PathVariable("uri") String uri, @RequestBody(required = false) String data) {

        // valid
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, HttpMethod not support.");
        }
        if (uri==null || uri.trim().length()==0) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping empty.");
        }
        if (XxlJobAdminConfig.getAdminConfig().getAccessToken()!=null
                && XxlJobAdminConfig.getAdminConfig().getAccessToken().trim().length()>0
                && !XxlJobAdminConfig.getAdminConfig().getAccessToken().equals(request.getHeader(XxlJobRemotingUtil.XXL_JOB_ACCESS_TOKEN))) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "The access token is wrong.");
        }

        // services mapping
        if ("callback".equals(uri)) {
            // 客户端触发器执行回调
            List<HandleCallbackParam> callbackParamList = GsonTool.fromJson(data, List.class, HandleCallbackParam.class);
            /**
             * @see AdminBizImpl#callback(java.util.List)
             */
            return adminBiz.callback(callbackParamList);
        } else if ("registry".equals(uri)) {
            // 注册服务
            RegistryParam registryParam = GsonTool.fromJson(data, RegistryParam.class);
            return adminBiz.registry(registryParam);
        } else if ("registryRemove".equals(uri)) {
            RegistryParam registryParam = GsonTool.fromJson(data, RegistryParam.class);
            return adminBiz.registryRemove(registryParam);
        } else {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping("+ uri +") not found.");
        }

    }

}
