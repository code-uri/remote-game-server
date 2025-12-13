package aimlabs.gaming.rgs.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.hierarchicalroles.CycleInRoleHierarchyException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;

@Slf4j
public class DefaultRoleHierarchy extends RoleHierarchyImpl {


    /**
     * Raw hierarchy configuration where each line represents single or multiple level
     * role chain.
     */
    private String roleHierarchyStringRepresentation = null;

    /**
     * {@code rolesReachableInOneStepMap} is a Map that under the key of a specific role
     * name contains a set of all roles reachable from this role in 1 step (i.e. parsed
     * {@link #roleHierarchyStringRepresentation} grouped by the higher role)
     */
    private Map<String, Set<GrantedAuthority>> rolesReachableInOneStepMap = null;

    /**
     * {@code rolesReachableInOneOrMoreStepsMap} is a Map that under the key of a specific
     * role name contains a set of all roles reachable from this role in 1 or more steps
     * (i.e. fully resolved hierarchy from {@link #rolesReachableInOneStepMap})
     */
    private Map<String, Set<GrantedAuthority>> rolesReachableInOneOrMoreStepsMap = null;

    /**
     * Set the role hierarchy and pre-calculate for every role the set of all reachable
     * roles, i.e. all roles lower in the hierarchy of every given role. Pre-calculation
     * is done for performance reasons (reachable roles can then be calculated in O(1)
     * time). During pre-calculation, cycles in role hierarchy are detected and will cause
     * a <tt>CycleInRoleHierarchyException</tt> to be thrown.
     * @param roleHierarchyStringRepresentation - String definition of the role hierarchy.
     */
    public void setHierarchy(String roleHierarchyStringRepresentation) {
        this.roleHierarchyStringRepresentation = roleHierarchyStringRepresentation;
        log.debug("setHierarchy() - The following role hierarchy was set: {}",
                roleHierarchyStringRepresentation);
        buildRolesReachableInOneStepMap();
        buildRolesReachableInOneOrMoreStepsMap();
    }

    
    public Collection<GrantedAuthority> getReachableGrantedAuthorities(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return AuthorityUtils.NO_AUTHORITIES;
        }
        Set<GrantedAuthority> reachableRoles = new HashSet<>();
        Set<String> processedNames = new HashSet<>();
        for (GrantedAuthority authority : authorities) {
            // Do not process authorities without string representation
            if (authority.getAuthority() == null) {
                reachableRoles.add(authority);
                continue;
            }

            if(!authority.getAuthority().startsWith("ROLE_"))
                continue;

            // Do not process already processed roles
            if (!processedNames.add(authority.getAuthority())) {
                continue;
            }
            // Add original authority
            reachableRoles.add(authority);
            // Add roles reachable in one or more steps
            Set<GrantedAuthority> lowerRoles = this.rolesReachableInOneOrMoreStepsMap.get(authority.getAuthority());
            if (lowerRoles == null) {
                continue; // No hierarchy for the role
            }
            for (GrantedAuthority role : lowerRoles) {
                if (processedNames.add(role.getAuthority())) {
                    reachableRoles.add(role);
                }
            }
        }
        log.debug(
                "getReachableGrantedAuthorities() - From the roles {} one can reach {} in zero or more steps.",
                authorities, reachableRoles);
        return new ArrayList<>(reachableRoles);
    }


    /**
     * Parse input and build the map for the roles reachable in one step: the higher role
     * will become a key that references a set of the reachable lower roles.
     */
    private void buildRolesReachableInOneStepMap() {
        this.rolesReachableInOneStepMap = new HashMap<>();
        for (String line : this.roleHierarchyStringRepresentation.split("\n")) {
            // Split on > and trim excessive whitespace
            String[] roles = line.trim().split("\\s+>\\s+");
            for (int i = 1; i < roles.length; i++) {
                String higherRole = roles[i - 1];
                GrantedAuthority lowerRole = new SimpleGrantedAuthority(roles[i]);
                Set<GrantedAuthority> rolesReachableInOneStepSet;
                if (!this.rolesReachableInOneStepMap.containsKey(higherRole)) {
                    rolesReachableInOneStepSet = new HashSet<>();
                    this.rolesReachableInOneStepMap.put(higherRole, rolesReachableInOneStepSet);
                }
                else {
                    rolesReachableInOneStepSet = this.rolesReachableInOneStepMap.get(higherRole);
                }
                rolesReachableInOneStepSet.add(lowerRole);
                log.debug(
                        "buildRolesReachableInOneStepMap() - From role {} one can reach role {} in one step.",
                        higherRole, lowerRole);
            }
        }
    }

    /**
     * For every higher role from rolesReachableInOneStepMap store all roles that are
     * reachable from it in the map of roles reachable in one or more steps. (Or throw a
     * CycleInRoleHierarchyException if a cycle in the role hierarchy definition is
     * detected)
     */
    private void buildRolesReachableInOneOrMoreStepsMap() {
        this.rolesReachableInOneOrMoreStepsMap = new HashMap<>();
        // iterate over all higher roles from rolesReachableInOneStepMap
        for (String roleName : this.rolesReachableInOneStepMap.keySet()) {
            Set<GrantedAuthority> rolesToVisitSet = new HashSet<>(this.rolesReachableInOneStepMap.get(roleName));
            Set<GrantedAuthority> visitedRolesSet = new HashSet<>();
            while (!rolesToVisitSet.isEmpty()) {
                // take a role from the rolesToVisit set
                GrantedAuthority lowerRole = rolesToVisitSet.iterator().next();
                rolesToVisitSet.remove(lowerRole);
                if (!visitedRolesSet.add(lowerRole)
                    || !this.rolesReachableInOneStepMap.containsKey(lowerRole.getAuthority())) {
                    continue; // Already visited role or role with missing hierarchy
                }
                else if (roleName.equals(lowerRole.getAuthority())) {
                    throw new CycleInRoleHierarchyException();
                }
                rolesToVisitSet.addAll(this.rolesReachableInOneStepMap.get(lowerRole.getAuthority()));
            }
            this.rolesReachableInOneOrMoreStepsMap.put(roleName, visitedRolesSet);
            log.debug(
                    "buildRolesReachableInOneOrMoreStepsMap() - From role {} one can reach {} in one or more steps.",
                    roleName, visitedRolesSet);
        }

    }
}
