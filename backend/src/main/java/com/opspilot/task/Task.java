package com.opspilot.task;
import java.time.*; import com.opspilot.project.Project; import com.opspilot.user.User; import jakarta.persistence.*;
@Entity @Table(name="tasks") public class Task {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id",nullable=false) private Project project;
 @Column(nullable=false,length=200) private String title; @Column(columnDefinition="TEXT") private String description;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TaskStatus status;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TaskPriority priority;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assignee_id") private User assignee;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false) private User createdBy;
 @Column(name="due_date") private LocalDate dueDate; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected Task(){} public Task(Project p,String t,String d,TaskPriority pr,User a,User c,LocalDate due){project=p;title=t;description=d;priority=pr;assignee=a;createdBy=c;dueDate=due;status=TaskStatus.TODO;}
 @PrePersist void created(){Instant n=Instant.now();createdAt=n;updatedAt=n;} @PreUpdate void updated(){updatedAt=Instant.now();}
 public void update(String t,String d,TaskPriority p,User a,LocalDate due,boolean clearA,boolean clearDue){if(t!=null)title=t;if(d!=null)description=d;if(p!=null)priority=p;if(clearA)assignee=null;else if(a!=null)assignee=a;if(clearDue)dueDate=null;else if(due!=null)dueDate=due;}
 public void status(TaskStatus s){status=s;} public Long getId(){return id;} public Project getProject(){return project;} public String getTitle(){return title;} public String getDescription(){return description;} public TaskStatus getStatus(){return status;} public TaskPriority getPriority(){return priority;} public User getAssignee(){return assignee;} public User getCreatedBy(){return createdBy;} public LocalDate getDueDate(){return dueDate;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
