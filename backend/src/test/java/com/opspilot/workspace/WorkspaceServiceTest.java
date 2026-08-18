package com.opspilot.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import com.opspilot.workspace.dto.CreateWorkspaceRequest;
import com.opspilot.workspace.dto.UpdateWorkspaceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class WorkspaceServiceTest {
    @Test
    void creationCreatesWorkspaceAndOwnerMembershipInOneServiceOperation() throws Exception {
        Store store = new Store();
        User owner = store.addUser("owner@example.com", "Owner");
        WorkspaceService service = store.service();

        var response = service.createWorkspace(owner.getId(), new CreateWorkspaceRequest("  Joan Workspace  "));

        assertEquals("Joan Workspace", response.name());
        assertEquals(WorkspaceRole.OWNER, response.role());
        assertEquals(owner.getId(), response.owner().id());
        assertEquals(1, store.workspaces.size());
        assertEquals(1, store.memberships.size());
        assertEquals(WorkspaceRole.OWNER, store.memberships.get(0).getRole());
    }

    @Test
    void listOnlyReturnsWorkspacesForCurrentMember() throws Exception {
        Store store = new Store();
        User owner = store.addUser("owner@example.com", "Owner");
        User other = store.addUser("other@example.com", "Other");
        WorkspaceService service = store.service();
        service.createWorkspace(owner.getId(), new CreateWorkspaceRequest("Owner Workspace"));
        service.createWorkspace(other.getId(), new CreateWorkspaceRequest("Other Workspace"));

        var workspaces = service.listWorkspaces(owner.getId());
        assertEquals(1, workspaces.size());
        assertEquals("Owner Workspace", workspaces.getFirst().name());
    }

    @Test
    void memberCanFetchButNonMemberReceivesNotFound() throws Exception {
        Store store = new Store();
        User owner = store.addUser("owner@example.com", "Owner");
        User member = store.addUser("member@example.com", "Member");
        User outsider = store.addUser("outside@example.com", "Outside");
        WorkspaceService service = store.service();
        var workspace = service.createWorkspace(owner.getId(), new CreateWorkspaceRequest("Private"));
        store.addMembership(store.workspaces.getFirst(), member, WorkspaceRole.MEMBER);

        assertEquals("Private", service.getWorkspace(workspace.id(), member.getId()).name());
        assertThrows(WorkspaceNotFoundException.class, () -> service.getWorkspace(workspace.id(), outsider.getId()));
    }

    @Test
    void ownerAndAdminCanRenameButMemberCannot() throws Exception {
        Store store = new Store();
        User owner = store.addUser("owner@example.com", "Owner");
        User admin = store.addUser("admin@example.com", "Admin");
        User member = store.addUser("member@example.com", "Member");
        WorkspaceService service = store.service();
        var workspace = service.createWorkspace(owner.getId(), new CreateWorkspaceRequest("Initial"));
        Workspace entity = store.workspaces.getFirst();
        store.addMembership(entity, admin, WorkspaceRole.ADMIN);
        store.addMembership(entity, member, WorkspaceRole.MEMBER);

        assertEquals("Owner Rename", service.updateWorkspace(workspace.id(), owner.getId(),
                new UpdateWorkspaceRequest("Owner Rename")).name());
        assertEquals("Admin Rename", service.updateWorkspace(workspace.id(), admin.getId(),
                new UpdateWorkspaceRequest("Admin Rename")).name());
        assertThrows(InsufficientWorkspaceRoleException.class, () -> service.updateWorkspace(workspace.id(), member.getId(),
                new UpdateWorkspaceRequest("Blocked Rename")));
    }

    @Test
    void duplicateMembershipIsRejectedByRepositoryConstraintGuard() throws Exception {
        Store store = new Store();
        User owner = store.addUser("owner@example.com", "Owner");
        WorkspaceService service = store.service();
        service.createWorkspace(owner.getId(), new CreateWorkspaceRequest("Workspace"));
        Workspace workspace = store.workspaces.getFirst();

        assertThrows(DataIntegrityViolationException.class,
                () -> store.addMembership(workspace, owner, WorkspaceRole.OWNER));
    }

    private static final class Store {
        final List<User> users = new ArrayList<>();
        final List<Workspace> workspaces = new ArrayList<>();
        final List<WorkspaceMember> memberships = new ArrayList<>();
        long nextUserId = 1, nextWorkspaceId = 1, nextMembershipId = 1;

        WorkspaceService service() {
            return new WorkspaceService(workspaceRepository(), memberRepository(), userRepository());
        }
        User addUser(String email, String name) throws Exception {
            User user = new User(email, "hash", name);
            invokeLifecycle(user, "setCreationTimestamps");
            setId(user, nextUserId++);
            users.add(user);
            return user;
        }
        WorkspaceMember addMembership(Workspace workspace, User user, WorkspaceRole role) throws Exception {
            if (memberships.stream().anyMatch(m -> m.getWorkspace().getId().equals(workspace.getId())
                    && m.getUser().getId().equals(user.getId()))) throw new DataIntegrityViolationException("duplicate");
            WorkspaceMember member = new WorkspaceMember(workspace, user, role);
            invokeLifecycle(member, "setJoinedAt");
            setId(member, nextMembershipId++);
            memberships.add(member);
            return member;
        }
        UserRepository userRepository() { return proxy(UserRepository.class, (method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(users.stream().filter(u -> u.getId().equals(args[0])).findFirst().orElse(null));
            default -> unsupported(method);
        }); }
        WorkspaceRepository workspaceRepository() { return proxy(WorkspaceRepository.class, (method, args) -> switch (method.getName()) {
            case "saveAndFlush" -> saveWorkspace((Workspace) args[0]);
            default -> unsupported(method);
        }); }
        WorkspaceMemberRepository memberRepository() { return proxy(WorkspaceMemberRepository.class, (method, args) -> switch (method.getName()) {
            case "saveAndFlush" -> addMembership(((WorkspaceMember) args[0]).getWorkspace(), ((WorkspaceMember) args[0]).getUser(), ((WorkspaceMember) args[0]).getRole());
            case "findByWorkspace_IdAndUser_Id" -> memberships.stream().filter(m -> m.getWorkspace().getId().equals(args[0]) && m.getUser().getId().equals(args[1])).findFirst();
            case "findByUser_IdOrderByWorkspace_CreatedAtDesc" -> memberships.stream().filter(m -> m.getUser().getId().equals(args[0])).toList();
            case "findByWorkspace_IdOrderByJoinedAtAsc" -> memberships.stream().filter(m -> m.getWorkspace().getId().equals(args[0])).toList();
            default -> unsupported(method);
        }); }
        private Workspace saveWorkspace(Workspace workspace) throws Exception {
            if (workspace.getId() == null) { invokeLifecycle(workspace, "setCreationTimestamps"); setId(workspace, nextWorkspaceId++); workspaces.add(workspace); }
            else invokeLifecycle(workspace, "setUpdateTimestamp");
            return workspace;
        }
        @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type, RepositoryHandler handler) {
            InvocationHandler invocation = (proxy, method, args) -> method.getName().equals("toString") ? "Store" : handler.call(method, args);
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocation);
        }
        private static Object unsupported(Method method) { throw new UnsupportedOperationException(method.getName()); }
        private static void setId(Object target, long id) throws Exception {
            Field field = target.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(target, id);
        }
        private static void invokeLifecycle(Object target, String name) throws Exception {
            Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
        }
        @FunctionalInterface interface RepositoryHandler { Object call(Method method, Object[] args) throws Throwable; }
    }
}
