package learning.tasklistener;

import org.activiti.engine.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * Created by renqun.yuan on 2016/2/18.
 */
@Component
public class TaskListener implements org.activiti.engine.delegate.TaskListener {
    public void notify(DelegateTask delegateTask) {
        delegateTask.addCandidateUser("bar");
    }
}
