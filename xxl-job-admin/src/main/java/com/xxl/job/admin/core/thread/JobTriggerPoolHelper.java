package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.trigger.XxlJobTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * job trigger thread pool helper
 *
 * @author xuxueli 2018-07-03 21:08:07
 */
public class JobTriggerPoolHelper {
    private static Logger logger = LoggerFactory.getLogger(JobTriggerPoolHelper.class);


    // ---------------------- trigger pool ----------------------

    // fast/slow thread pool
    private ThreadPoolExecutor fastTriggerPool = null;
    private ThreadPoolExecutor slowTriggerPool = null;

    /**
     * 这里分别初始化了2个线程池，一个快一个慢，优先选择快，当一分钟以内任务超过10次执行时间超过500ms，则加入慢线程池执行
     */
    public void start(){
        // 初始化 快触发器线程池: 最大线程数200，阻塞队列长度: 1000
        fastTriggerPool = new ThreadPoolExecutor(
                10,
                XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(1000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobTriggerPoolHelper-fastTriggerPool-" + r.hashCode());
                    }
                });
        // 初始化 慢触发器线程池: 最大线程数100，阻塞队列长度: 2000
        // 一分钟内超时10次，则采用慢触发器执行
        slowTriggerPool = new ThreadPoolExecutor(
                10,
                XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(2000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobTriggerPoolHelper-slowTriggerPool-" + r.hashCode());
                    }
                });
    }


    public void stop() {
        //triggerPool.shutdown();
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
        logger.info(">>>>>>>>> xxl-job trigger thread pool shutdown success.");
    }


    // job timeout count
    private volatile long minTim = System.currentTimeMillis()/60000;     // ms > min
    // 用于缓存任务超时次数Map: <任务Id, 超时次数>
    private volatile ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();


    /**
     * add trigger
     * 执行触发器
     */
    public void addTrigger(final int jobId,
                           final TriggerTypeEnum triggerType,
                           final int failRetryCount,
                           final String executorShardingParam,
                           final String executorParam,
                           final String addressList) {

        // choose thread pool 选择线程池, 默认为快触发器线程池
        ThreadPoolExecutor triggerPool_ = fastTriggerPool;
        // 根据任务id 获取 一分钟内超时次数
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);
        // 如果一分钟内超时10次, 则改为慢触发器线程池执行
        if (jobTimeoutCount!=null && jobTimeoutCount.get() > 10) {      // job-timeout 10 times in 1 min
            triggerPool_ = slowTriggerPool;
        }

        // trigger
        // 触发器线程池执行线程
        triggerPool_.execute(new Runnable() {
            @Override
            public void run() {

                long start = System.currentTimeMillis();

                try {
                    // do trigger 触发
                    XxlJobTrigger.trigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam, addressList);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {

                    // check timeout-count-map
                    long minTim_now = System.currentTimeMillis()/60000;
                    // 如果间隔时间不是1分钟, 则清空jobTimeoutCountMap(任务超时计数Map)
                    if (minTim != minTim_now) {
                        minTim = minTim_now;
                        jobTimeoutCountMap.clear();
                    }

                    // incr timeout-count-map
                    long cost = System.currentTimeMillis()-start;
                    if (cost > 500) {       // ob-timeout threshold 500ms
                        /**
                         * 如果任务执行时长 > 500毫秒, 则将当前任务加入到jobTimeoutCountMap(任务超时计数Map), 累计计数器
                         *
                         * putIfAbsent:
                         * 当key在Map中不存在时: 会put键值对, key - value, 并返回原来的值, 原来的值为null, 所以返回null
                         * 当key在Map中存在时: 不会put, 直接返回原来的值.
                         *
                         * 这里当第一次来的时候, put了一个值为1原子类AtomicInteger作为value, 并返回null,
                         * 第二次来: 不做修改, 返回值为1原子类AtomicInteger, 后续执行对原子类累加, 原子类值为2
                         * 第三次来: 不做修改, 返回值为2原子类AtomicInteger, 后续执行对原子类累加, 原子类值为3
                         * ...
                         *
                         * 这里要了解AtomicInteger的引用传递.
                         */
                        AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                        if (timeoutCount != null) {
                            timeoutCount.incrementAndGet();
                        }
                    }

                }

            }
        });
    }



    // ---------------------- helper ----------------------

    private static JobTriggerPoolHelper helper = new JobTriggerPoolHelper();

    /**
     * 初始化触发器线程池
     */
    public static void toStart() {
        helper.start();
    }
    public static void toStop() {
        helper.stop();
    }

    /**
     * 执行触发器
     * @param jobId
     * @param triggerType
     * @param failRetryCount
     * 			>=0: use this param
     * 			<0: use param from job info config
     * @param executorShardingParam
     * @param executorParam
     *          null: use job param
     *          not null: cover job param
     */
    public static void trigger(int jobId, TriggerTypeEnum triggerType, int failRetryCount, String executorShardingParam, String executorParam, String addressList) {
        helper.addTrigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam, addressList);
    }

}
