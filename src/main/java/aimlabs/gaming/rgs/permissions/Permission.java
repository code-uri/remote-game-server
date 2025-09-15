package aimlabs.gaming.rgs.permissions;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.ObjectInputStream;

@Data
@NoArgsConstructor
public class Permission extends BaseDto {

    protected String userId;
    protected String permission;

    protected String actions;
    protected String name;
    protected String entity;

    // does this permission have a wildcard at the end?
    protected transient boolean wildcard;

    // the name without the wildcard on the end
    protected transient String path;

    /**
     * initialize a BasicPermission object. Common to all constructors.
     */
    private void init(String name) {
        if (name == null)
            throw new NullPointerException("name can't be null");

        int len = name.length();

        if (len == 0) {
            throw new IllegalArgumentException("name can't be empty");
        }

        char last = name.charAt(len - 1);

        // Is wildcard or ends with ".*"?
        if (last == '*' && (len == 1 || name.charAt(len - 2) == '.')) {
            wildcard = true;
            if (len == 1) {
                path = "";
            } else {
                path = name.substring(0, len - 1);
            }
        }
        else if (name.trim().equals("*.*")) {
            wildcard = true;
            path = "";
        }
        else if (name.trim().startsWith("*.")) {
            wildcard = true;
            if(len == 2)
                path = "";
            else {
                path = name.substring(2, len);
            }
        }
        else {
            path = name;
        }
    }


    /**
     * Creates a new BasicPermission with the specified name.
     * Name is the symbolic name of the permission, such as
     * "setFactory",
     * "print.queueJob", or "topLevelWindow", etc.
     *
     * @param name the name of the BasicPermission.
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public Permission(String name) {
        this.name = name;
        init(name);
    }


    /**
     * Creates a new BasicPermission object with the specified name.
     * The name is the symbolic name of the BasicPermission, and the
     * actions String is currently unused.
     *
     * @param name the name of the BasicPermission.
     * @param actions ignored.
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public Permission(String name, String actions) {
        this(name);
        this.actions = actions;
    }

    /**
     * Checks if the specified permission is "implied" by
     * this object.
     * <P>
     * More specifically, this method returns true if:
     * <ul>
     * <li> {@code p}'s class is the same as this object's class, and
     * <li> {@code p}'s name equals or (in the case of wildcards)
     *      is implied by this object's
     *      name. For example, "a.b.*" implies "a.b.c" but not *.b.c to a.b.c
     * </ul>
     *
     * @param p the permission to check against.
     *
     * @return true if the passed permission is equal to or
     * implied by this permission, false otherwise.
     */
    public boolean implies(Permission p) {
        if ((p == null) || (p.getClass() != getClass()))
            return false;

        Permission that = (Permission) p;

        if (this.wildcard) {
            if (that.wildcard) {
                // one wildcard can imply another
                return that.path.startsWith(path);
            } else {
                // make sure ap.path is longer so a.b.* doesn't imply a.b
                return (that.path.length() > this.path.length() && that.path.startsWith(this.path));
                    /*return (that.path.length() > this.path.length()) &&
                       (that.path.startsWith(this.path) ||
                        //trying to match *.someMethod to svc.someMethod
                        (this.path.startsWith("*.") && that.path.substring(0,that.path.indexOf(".")).equals(this.path)));*/
            }
        } else {
            if (that.wildcard) {
                // a non-wildcard can't imply a wildcard
                return false;
            }
            else {
                return this.path.equals(that.path);
            }
        }
    }

    /**
     * Checks two BasicPermission objects for equality.
     * Checks that {@code obj}'s class is the same as this object's class
     * and has the same name as this object.
     *
     * @param obj the object we are testing for equality with this object.
     * @return true if {@code obj}'s class is the same as this object's class
     *  and has the same name as this BasicPermission object, false otherwise.
     */

    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if ((obj == null) || (obj.getClass() != getClass()))
            return false;

        Permission bp = (Permission) obj;

        return getName().equals(bp.getName());
    }


    /**
     * Returns the hash code value for this object.
     * The hash code used is the hash code of the name, that is,
     * {@code getName().hashCode()}, where {@code getName} is
     * from the IPermission superclass.
     *
     * @return a hash code value for this object.
     */

    public int hashCode() {
        return this.getName().hashCode();
    }

    /**
     * Returns the canonical string representation of the actions,
     * which currently is the empty string "", since there are no actions for
     * a BasicPermission.
     *
     * @return the empty string "".
     */

    public String getActions() {
        return "";
    }


    /**
     * readObject is called to restore the state of the BasicPermission from
     * a stream.
     *
     * @param  s the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        // init is called to initialize the rest of the values.
        init(getName());
    }

    /**
     * Returns the canonical name of this BasicPermission.
     * All internal invocations of getName should invoke this method, so
     * that the pre-JDK 1.6 "exitVM" and current "exitVM.*" permission are
     * equivalent in equals/hashCode methods.
     *
     * @return the canonical name of this BasicPermission.
     */
    final String getCanonicalName() {
        return getName();
    }


    public String asString() {
        return Permission.super.toString();
    }
}
