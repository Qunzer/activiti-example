package learning.service.impl;

import learning.service.ActivitiProcessInstanceService;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.Task;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * Created by yrq on 2016/2/18.
 */
@Service
public class ActivitiProcessInstanceServiceImpl implements ActivitiProcessInstanceService {
    @Resource
    private RuntimeService runtimeService;
    @Resource
    private TaskService taskService;
    @Resource
    private ProcessEngine processEngine;
    @Resource
    private RepositoryService repositoryService;

    /**
     * 启动流程
     *
     * @param processKey 流程文件的key
     * @param initVarMap 初始化变量
     * @return 返回启动的流程实例ID
     */
    public String start(String processKey, Map<String, Object> initVarMap) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, initVarMap);
        return processInstance.getId();
    }

    /**
     * 审批人拒绝流程
     *
     * @param user              审批人
     * @param processInstanceId 流程实例ID
     * @param reason            拒绝原因
     */
    public void reject(String user, String processInstanceId, String reason) {
        Task task = taskService.createTaskQuery().taskCandidateOrAssigned(user).processInstanceId(processInstanceId).active().singleResult();
        // 能够在流程流程历史里面查询归属人
        task.setOwner(user);
        taskService.saveTask(task);
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    /**
     * 流程发起人主动取消
     *
     * @param processInstanceId 流程实例ID
     * @param reason            取消原因
     */
    public void cancle(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    /**
     * 审批人同意流程
     *
     * @param user              审批人
     * @param processInstanceId 流程实例Id
     */
    public void agree(String user, String processInstanceId) {
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).taskCandidateOrAssigned(user).singleResult();
        // 如果同意任务需要添加一些变量值， 比如单实例任务是否完成等
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("complete", true);
        task.setOwner(user);
        taskService.saveTask(task);
        taskService.complete(task.getId(), vars);
    }

    /**
     * 加签
     *
     * @param candidateUser     审核人
     * @param endorseUser       被加签人
     * @param processInstanceId 流程实例ID
     */
    public void endorse(String candidateUser, String endorseUser, String processInstanceId) {
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).taskCandidateOrAssigned(candidateUser).singleResult();
        // 该变量保存一个是否完成任务实例的标志
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("complete", false);
        task.setOwner(candidateUser);
        taskService.saveTask(task);
        taskService.complete(task.getId(), vars);

        Task endorseTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

        taskService.addUserIdentityLink(endorseTask.getId(), endorseUser, IdentityLinkType.PARTICIPANT);
    }

    /**
     * 召回 回退功能， 当下一个节点没有被审核时，召回到上一个节点
     *
     * @param user
     * @param processInstanceId
     */
    public void back(String user, String processInstanceId) {
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).active().singleResult();

        ActivityImpl currActivity = findActivitiImpl(task.getId(), null);
        List<PvmTransition> oriPvmTransitionList = clearTransition(currActivity);
        TransitionImpl newTransition = currActivity.createOutgoingTransition();
        ActivityImpl pointActivity = findActivitiImpl(task.getId(), task.getTaskDefinitionKey());
        newTransition.setDestination(pointActivity);
        try {
            Map<String, Object> vars = new HashMap<String, Object>();
            vars.put("complete", true);
            task.setOwner(user);
            taskService.saveTask(task);
            taskService.complete(task.getId(), vars);
        } finally {
            pointActivity.getIncomingTransitions().remove(newTransition);
            restoreTransition(currActivity, oriPvmTransitionList);
            Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).active().singleResult();
            taskService.setAssignee(currentTask.getId(), user);

        }
    }

    private List<PvmTransition> clearTransition(ActivityImpl activityImpl) {
        List<PvmTransition> oriPvmTransitionList = new ArrayList<PvmTransition>();
        List<PvmTransition> pvmTransitionList = activityImpl.getOutgoingTransitions();
        for (PvmTransition pvmTransition : pvmTransitionList) {
            oriPvmTransitionList.add(pvmTransition);
        }
        pvmTransitionList.clear();
        return oriPvmTransitionList;
    }

    private void restoreTransition(ActivityImpl activityImpl, List<PvmTransition> oriPvmTransitionList) {
        List<PvmTransition> pvmTransitionList = activityImpl.getOutgoingTransitions();
        pvmTransitionList.clear();
        for (PvmTransition pvmTransition : oriPvmTransitionList) {
            pvmTransitionList.add(pvmTransition);
        }
    }

    private ActivityImpl findActivitiImpl(String taskId, String activityId) {
        TaskEntity task = (TaskEntity) taskService.createTaskQuery().taskId(taskId).singleResult();
        ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(task.getProcessDefinitionId());
        if (activityId == null || activityId.length() == 0) {
            activityId = task.getTaskDefinitionKey();
        }
        if (activityId.toUpperCase().equals("END")) {
            for (ActivityImpl activityImpl : processDefinition.getActivities()) {
                List<PvmTransition> pvmTransitionList = activityImpl.getOutgoingTransitions();
                if (pvmTransitionList.isEmpty()) {
                    return activityImpl;
                }
            }
        }
        return processDefinition.findActivity(activityId);
    }

    /**
     * 生成流程图的二级制文件流
     *
     * @param processKey        流程图的key
     * @param processInstanceId 流程实例ID
     * @param waterMarkText     如果给流程图添加水印，标识水印内容
     * @return
     */
    public byte[] generatePic(String processKey, String processInstanceId, String waterMarkText) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processKey).latestVersion().singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        List<Task> list = taskService.createTaskQuery().active().processInstanceId(processInstanceId).list();
        List<String> activeActivityIds = new ArrayList<String>();
        for (Task task : list) {
            activeActivityIds.add(task.getTaskDefinitionKey());
        }
        ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        String activityFontName = processEngineConfiguration.getActivityFontName();
        String labelFontName = processEngineConfiguration.getLabelFontName();
        InputStream imageStream = processEngineConfiguration.getProcessDiagramGenerator().generateDiagram(bpmnModel, "png", activeActivityIds, Collections.<String>emptyList(), activityFontName, labelFontName, null, 1.0);
        ByteArrayOutputStream outputStream = null;
        try {
            BufferedImage bufferedImage = ImageIO.read(imageStream);
            Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
            AlphaComposite alphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f);
            graphics.setComposite(alphaComposite);
            graphics.setColor(Color.BLUE);
            graphics.setFont(new Font("simsun", Font.BOLD, 32));
            FontMetrics fontMetrics = graphics.getFontMetrics();
            Rectangle2D rect = fontMetrics.getStringBounds(waterMarkText, graphics);

            // calculates the coordinate where the String is painted
            int centerX = (bufferedImage.getWidth() - (int) rect.getWidth()) / 2;
            int centerY = bufferedImage.getHeight() / 2;

            // paints the textual watermark
            graphics.drawString(waterMarkText, centerX, centerY);
            outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (imageStream != null) try {
                imageStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (outputStream != null) try {
                outputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
