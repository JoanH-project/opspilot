package com.opspilot.task;
import java.util.*; import com.opspilot.project.*; import com.opspilot.task.dto.*; import com.opspilot.user.*; import com.opspilot.workspace.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class TaskService {
 private final TaskRepository tasks; private final ProjectRepository projects; private final UserRepository users; private final WorkspaceAccessService access;
 public TaskService(TaskRepository t,ProjectRepository p,UserRepository u,WorkspaceAccessService a){tasks=t;projects=p;users=u;access=a;}
 @Transactional public TaskResponse create(Long projectId,Long userId,CreateTaskRequest r){Project p=project(projectId,userId);editable(p);User creator=users.findById(userId).orElseThrow(TaskNotFoundException::new);User assignee=assignee(p,r.assigneeId());return TaskResponse.from(tasks.saveAndFlush(new Task(p,r.title().trim(),norm(r.description()),r.priority()==null?TaskPriority.MEDIUM:r.priority(),assignee,creator,r.dueDate())));}
 @Transactional(readOnly=true) public List<TaskResponse> list(Long projectId,Long userId,TaskStatus s,TaskPriority p,Long assigneeId){Project project=project(projectId,userId);return tasks.findByProject_IdOrderByCreatedAtDesc(project.getId()).stream().filter(t->s==null||t.getStatus()==s).filter(t->p==null||t.getPriority()==p).filter(t->assigneeId==null||(t.getAssignee()!=null&&t.getAssignee().getId().equals(assigneeId))).map(TaskResponse::from).toList();}
 @Transactional(readOnly=true) public TaskResponse get(Long id,Long user){return TaskResponse.from(task(id,user));}
 @Transactional public TaskResponse update(Long id,Long user,UpdateTaskRequest r){Task t=task(id,user);editable(t.getProject());if(r.title()!=null&&r.title().isBlank())throw new InvalidTaskUpdateException("Task title must not be blank");User a=r.clearAssignee()?null:assignee(t.getProject(),r.assigneeId());t.update(r.title()==null?null:r.title().trim(),norm(r.description()),r.priority(),a,r.dueDate(),r.clearAssignee(),r.clearDueDate());return TaskResponse.from(tasks.saveAndFlush(t));}
 @Transactional public TaskResponse status(Long id,Long user,UpdateTaskStatusRequest r){Task t=task(id,user);editable(t.getProject());t.status(r.status());return TaskResponse.from(tasks.saveAndFlush(t));}
 private Task task(Long id,Long user){Task t=tasks.findById(id).orElseThrow(TaskNotFoundException::new);access.requireMembership(t.getProject().getWorkspace().getId(),user);return t;}
 private Project project(Long id,Long user){Project p=projects.findById(id).orElseThrow(ProjectNotFoundException::new);access.requireMembership(p.getWorkspace().getId(),user);return p;}
 private void editable(Project p){if(p.getStatus()==ProjectStatus.ARCHIVED)throw new ArchivedProjectException();}
 private User assignee(Project p,Long id){if(id==null)return null;User u=users.findById(id).orElseThrow(InvalidTaskAssigneeException::new);try{access.requireMembership(p.getWorkspace().getId(),u.getId());return u;}catch(WorkspaceNotFoundException e){throw new InvalidTaskAssigneeException();}}
 private String norm(String s){return s==null?null:s.trim();}
}
