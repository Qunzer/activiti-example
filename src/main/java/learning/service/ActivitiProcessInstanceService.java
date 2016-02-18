package learning.service;

import java.util.Map;

/**
 * Created by yrq on 2016/2/18.
 */
public interface ActivitiProcessInstanceService {
    /**
     * 启动流程
     *
     * @param processKey 流程文件的key
     * @param initVarMap 初始化变量
     * @return 返回启动的流程实例ID
     */
    String start(String processKey, Map<String, Object> initVarMap);

    /**
     * 审批人拒绝流程
     *
     * @param user              审批人
     * @param processInstanceId 流程实例ID
     * @param reason            拒绝原因
     */
    void reject(String user, String processInstanceId, String reason);

    /**
     * 流程发起人主动取消
     *
     * @param processInstanceId 流程实例ID
     * @param reason            取消原因
     */
    void cancle(String processInstanceId, String reason);

    /**
     * 审批人同意流程
     *
     * @param user              审批人
     * @param processInstanceId 流程实例Id
     */
    void agree(String user, String processInstanceId);

    /**
     * 加签
     *
     * @param candidateUser     审核人
     * @param endorseUser       被加签人
     * @param processInstanceId 流程实例ID
     */
    void endorse(String candidateUser, String endorseUser, String processInstanceId);

    /**
     * 召回 回退功能， 当下一个节点没有被审核时，召回到上一个节点
     *
     * @param user
     * @param processInstanceId
     */
    void back(String user, String processInstanceId);

    /**
     * 生成流程图的二级制文件流
     *
     * @param processKey        流程图的key
     * @param processInstanceId 流程实例ID
     * @param waterMarkText     如果给流程图添加水印，标识水印内容
     * @return
     */
    byte[] generatePic(String processKey, String processInstanceId, String waterMarkText);
}
