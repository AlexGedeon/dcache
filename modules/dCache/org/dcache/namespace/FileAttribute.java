package org.dcache.namespace;

import static org.dcache.acl.enums.FileAttribute.*;

/**
 *
 * File attributes supported by dCache
 *
 * @since 1.9.4
 */
public enum FileAttribute {

    ACCESS_LATENCY,
    ACL,
    CHECKSUM,
    CREATION_TIME,
    DEFAULT_ACCESS_LATENCY,
    DEFAULT_RETENTION_POLICY,
    FLAGS,
    HSM,
    LOCATIONS,
    MODE,
    MODIFICATION_TIME,
    OWNER,
    OWNER_GROUP,
    PERMISSION,
    RETENTION_POLICY,
    SIZE,
    STORAGECLASS,
    TYPE;

    /**
     * Returns the matching NFS4 attribute, or null if there is
     * no matching attribute in NFS4.
     */
    public org.dcache.acl.enums.FileAttribute toNfs4Attribute()
    {
        switch (this) {
        case ACL:
            return FATTR4_ACL;
        case CREATION_TIME:
            return FATTR4_TIME_CREATE;
        case MODE:
            return FATTR4_MODE;
        case MODIFICATION_TIME:
            return FATTR4_TIME_MODIFY;
        case OWNER:
            return FATTR4_OWNER;
        case OWNER_GROUP:
            return FATTR4_OWNER_GROUP;
        case SIZE:
            return FATTR4_SIZE;
        case TYPE:
            return FATTR4_TYPE;
        default:
            return null;
        }
    }

    // parent
}