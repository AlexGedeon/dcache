package org.dcache.auth;

import diskCacheV111.util.*;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Basic;
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeSet;


@Entity
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
public class UserAuthRecord extends UserAuthBase
{
    private static final long serialVersionUID = 2212212275053022221L;

    @Basic
    public TreeSet<String> principals;
    @Basic
    public int[] GIDs;
    @Basic
    public int currentGIDindex=0;

    public UserAuthRecord(String user,
                          String DN,
                          String fqan,
			                    boolean readOnly,
                          int priority,
                          int uid,
                          int[] GIDs,
                          String home,
                          String root,
                          String fsroot,
                          HashSet<String> principals)
    {
        super(user, DN, fqan, readOnly, priority, uid, (GIDs!=null && GIDs.length>0) ? GIDs[0] : -1, home, root, fsroot);
        this.GIDs = GIDs;
        this.principals = new TreeSet<String>(principals);
    }

  public UserAuthRecord(String user,
                          String DN,
                          String fqan,
			                    boolean readOnly,
                          int priority,
                          int uid,
                          int gid,
                          String home,
                          String root,
                          String fsroot,
                          HashSet<String> principals)
    {
        this(user, DN, fqan, readOnly, priority, uid, new int[]{gid}, home, root, fsroot, principals);
    }


  public UserAuthRecord(String user,
			                    boolean readOnly,
                          int uid,
                          int[] GIDs,
                          String home,
                          String root,
                          String fsroot,
                          HashSet<String> principals)
    {
        super(user, readOnly, uid, (GIDs!=null && GIDs.length>0) ? GIDs[0] : -1, home, root, fsroot);
        this.GIDs = GIDs;
        this.principals = new TreeSet<String>(principals);
    }

  public UserAuthRecord(String user,
			                    boolean readOnly,
                          int uid,
                          int gid,
                          String home,
                          String root,
                          String fsroot,
                          HashSet<String> principals)
    {
        super(user, readOnly, uid, gid, home, root, fsroot);
        this.GIDs = new int[]{gid};
        this.principals = new TreeSet<String>(principals);
    }

    /**
     * nonprivate default constructor to sutisfy the JPA requirements
     */
    public UserAuthRecord() {
    }
    

    public void appendToStringBuffer(StringBuffer sb)
    {
        sb.append(Username);
        if(ReadOnly) {
            sb.append(" read-only");
        } else {
            sb.append(" read-write");
        }
        sb.append( ' ').append( UID).append( ' ');
        for(int i=0; i<GIDs.length ; ++i) {
         sb.append(GIDs[i]);
            if(i<GIDs.length-1) {
                sb.append(',');
            }
        }
        sb.append(' ');
        sb.append( Home ).append(' ');
        sb.append( Root ).append(' ');
        sb.append( FsRoot ).append('\n');
        if(principals != null)
        {
            for(String principal : principals)
            {
                sb.append("  ").append(principal).append('\n');
            }
        }
        return;
    }


    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append(Username);
        sb.append(' ').append( DN);
        sb.append(' ').append( getFqan());
          if(ReadOnly) {
            sb.append(" read-only");
          } else {
            sb.append(" read-write");
          }
        sb.append( ' ').append( UID).append( ' ');
        sb.append( Arrays.toString( GIDs ) ).append(' ');
        sb.append( Home ).append(' ');
        sb.append( Root ).append(' ');
        sb.append( FsRoot ).append('\n');
        if(principals != null)
        {
            for(String principal : principals)
            {
                sb.append("  ").append(principal).append('\n');
            }
        }
        return sb.toString();
    }

    public String toDetailedString()
    {
        StringBuffer sb = new StringBuffer(" User Authentication Record for ");
        sb.append(Username).append(" :\n");
        sb.append("             DN = ").append(DN).append('\n');
        sb.append("           FQAN = ").append(getFqan()).append('\n');
	      sb.append("      read-only = " + readOnlyStr() + "\n");
        sb.append("            UID = ").append(UID).append('\n');
        sb.append("           GIDs = ");
        for(int i=0; i<GIDs.length ; ++i) {
            sb.append(GIDs[i]);
            if(i<GIDs.length-1) {
                sb.append(',');
            }
        }
        sb.append('\n');
        sb.append("           Home = ").append(Home).append('\n');
        sb.append("           Root = ").append(Root).append('\n');
        sb.append("         FsRoot = ").append(FsRoot).append('\n');

        if(principals != null)
        {
            sb.append("         Secure Ids accepted by this user :\n");
            for(String principal : principals)
            {
                sb.append("    SecureId  = \"").append(principal).append("\"\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isAnonymous() { return false; }
    @Override
    public boolean isWeak() {return false; }

    public boolean hasSecureIdentity(String p)
    {
      if(principals!=null)
      {
          return principals.contains(p);
      }
      return false;
    }

    public boolean isValid()
    {
        return Username != null;
    }

    public void addSecureIdentity(String id)
    {
        principals.add(id);
    }

    public void addSecureIdentities(HashSet<String> ids)
    {
        principals.addAll(ids);
    }

    public void removeSecureIdentities(HashSet<String> ids)
    {
        principals.removeAll(ids);
    }

    @Override
    public int hashCode() {
        return UID;
    }

  @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || !(obj instanceof UserAuthRecord)) return false;
        UserAuthRecord r = (UserAuthRecord) obj;
        return Username.equals(r.Username) && ReadOnly == r.ReadOnly
                && UID == r.UID && Arrays.equals(GIDs, r.GIDs)
                && Home.equals(r.Home) && Root.equals(r.Root)
                && FsRoot.equals(r.FsRoot);
    }
}

