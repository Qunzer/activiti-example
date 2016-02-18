package learning.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by yrq on 2016/2/18.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"/applicationContext.xml"})
public class ActivitiProcessInstanceServiceTest {

    @Resource
    private ActivitiProcessInstanceService activitiProcessInstanceService;

    @Test
    public void testStart() throws Exception {
        // example.bpmn流程中的process key为：<process id="myProcess"
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("money", 6000);
        String processInstanceId = activitiProcessInstanceService.start("myProcess", vars);
        System.out.println("processInstanceId:" + processInstanceId);
    }

    public void testReject() throws Exception {

    }

    public void testCancle() throws Exception {

    }

    public void testAgree() throws Exception {

    }

    public void testEndorse() throws Exception {

    }

    public void testBack() throws Exception {

    }

    public void testGeneratePic() throws Exception {

    }
}