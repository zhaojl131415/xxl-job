package com.xxl.job.admin.core.route;

import com.xxl.job.admin.core.route.strategy.*;
import com.xxl.job.admin.core.util.I18nUtil;

/**
 * 路由策略: 当执行器集群部署时，提供丰富的路由策略，
 * Created by xuxueli on 17/3/10.
 */
public enum ExecutorRouteStrategyEnum {
    // （第一个）固定选择第一个机器
    FIRST(I18nUtil.getString("jobconf_route_first"), new ExecutorRouteFirst()),
    // （最后一个）固定选择最后一个机器
    LAST(I18nUtil.getString("jobconf_route_last"), new ExecutorRouteLast()),
    // （轮询）轮询
    ROUND(I18nUtil.getString("jobconf_route_round"), new ExecutorRouteRound()),
    // （随机）随机选择在线的机器
    RANDOM(I18nUtil.getString("jobconf_route_random"), new ExecutorRouteRandom()),
    // （一致性HASH）每个任务按照Hash算法固定选择某一台机器，且所有任务均匀散列在不同机器上
    CONSISTENT_HASH(I18nUtil.getString("jobconf_route_consistenthash"), new ExecutorRouteConsistentHash()),
    // （最不经常使用）使用频率最低的机器优先被选举
    LEAST_FREQUENTLY_USED(I18nUtil.getString("jobconf_route_lfu"), new ExecutorRouteLFU()),
    // （最近最久未使用）最久未使用的机器优先被选举
    LEAST_RECENTLY_USED(I18nUtil.getString("jobconf_route_lru"), new ExecutorRouteLRU()),
    // （故障转移）按照顺序依次进行心跳检测，第一个心跳检测成功的机器选定为目标执行器并发起调度
    FAILOVER(I18nUtil.getString("jobconf_route_failover"), new ExecutorRouteFailover()),
    // （忙碌转移）按照顺序依次进行空闲检测，第一个空闲检测成功的机器选定为目标执行器并发起调度
    BUSYOVER(I18nUtil.getString("jobconf_route_busyover"), new ExecutorRouteBusyover()),
    // (分片广播)广播触发对应集群中所有机器执行一次任务，同时系统自动传递分片参数；可根据分片参数开发分片任务
    SHARDING_BROADCAST(I18nUtil.getString("jobconf_route_shard"), null);
    // 子任务：每个任务都拥有一个唯一的任务ID(任务ID可以从任务列表获取)，当本任务执行结束并且执行成功时，将会触发子任务ID所对应的任务的一次主动调度。

    ExecutorRouteStrategyEnum(String title, ExecutorRouter router) {
        this.title = title;
        this.router = router;
    }

    private String title;
    private ExecutorRouter router;

    public String getTitle() {
        return title;
    }
    public ExecutorRouter getRouter() {
        return router;
    }

    public static ExecutorRouteStrategyEnum match(String name, ExecutorRouteStrategyEnum defaultItem){
        if (name != null) {
            for (ExecutorRouteStrategyEnum item: ExecutorRouteStrategyEnum.values()) {
                if (item.name().equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }

}
