/*
 *   DCAP - dCache Access Protocol client interface
 *
 *   Copyright (C) 2000,2004 DESY Hamburg DMG-Division.
 *
 *   AUTHOR: Tigran Mkrtchayn (tigran.mkrtchyan@desy.de)
 *   FIXED BY: Vladimir Podstavkov (podstvkv@fnal.gov)
 *
 *   This program can be distributed under the terms of the GNU LGPL.
 *   See the file COPYING.LIB
 *
 */
 
 
/*
 * $Id: system_io.c,v 1.52.2.1 2006-10-11 08:46:38 tigran Exp $
 */
 
#ifdef LIBC_SYSCALLS

#include <stdio.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include "dcap_debug.h"
#include "sysdep.h"
#include <signal.h>

static MUTEX(gLock);
static void *handle;

#ifndef LIBC
#define LIBC NULL
#endif
static char *libname = LIBC;
static int (*s_open)           (const char *, int, ...);
static ssize_t (*s_read)       (int, void *, size_t);
static ssize_t (*s_readv)      (int, const struct iovec *vector, int count);
static ssize_t (*s_pread)      (int, void *, size_t, off_t);
static ssize_t (*s_pread64)    (int, void *, size_t, off64_t);
static ssize_t (*s_write)      (int, const void *, size_t);
static ssize_t (*s_writev)     (int, const struct iovec *vector, int count);
static ssize_t (*s_pwrite)     (int, const void *, size_t, off_t);
static ssize_t (*s_pwrite64)   (int, const void *, size_t, off64_t);
static off64_t (*s_lseek64)    (int, off64_t, int);
static int (*s_close)          (int);

/* 
 * A version number is included in the x86 SVR4 stat interface
 * so that SVR3 binaries can be supported
 */
#ifdef _STAT_VER
static int (*s_stat)       (int , const char *, struct stat *);
static int (*s_fstat)      (int, int, struct stat *);
static int (*s_stat64)     (int , const char *, struct stat64 *);
static int (*s_lstat64)    (int , const char *, struct stat64 *);
static int (*s_lstat)      (int , const char *, struct stat *);
static int (*s_fstat64)    (int, int, struct stat64 *);
#else
static int (*s_stat)       (const char *, struct stat *);
static int (*s_fstat)      (int, struct stat *);
static int (*s_stat64)     (const char *, struct stat64 *);
static int (*s_lstat64)    (const char *, struct stat64 *);
static int (*s_lstat)      (const char *, struct stat *);
static int (*s_fstat64)    (int, struct stat64 *);
#endif
static int (*s_fsync)      (int);
static int (*s_dup)        (int);
static int              (*s_closedir)   (DIR *);
static DIR *            (*s_opendir)    (const char *);
static struct dirent *  (*s_readdir)    (DIR *);
static struct dirent64 *(*s_readdir64)  (DIR *);
static off_t            (*s_telldir)    (DIR *);
static void             (*s_seekdir)    (DIR *, off_t);
static int              (*s_unlink)(const char *);
static int              (*s_rmdir)(const char *);
static int              (*s_mkdir)(const char *, mode_t);
static int              (*s_chmod)(const char *, mode_t);
static int              (*s_chown)(const char *, uid_t, gid_t);
static int              (*s_access)(const char *, int);
static int              (*s_rename)(const char *, const char *);

static FILE * (*s_fopen)(const char *, const char *);
static FILE * (*s_fopen64)(const char *, const char *);
static FILE * (*s_fdopen)(int, const char *);
static int  (*s_fclose)(FILE *);
static size_t (*s_fwrite)(const void *, size_t, size_t, FILE *);
static size_t (*s_fread)(void *, size_t, size_t, FILE *);
static int (*s_fseeko64)(FILE *, off64_t, int);
static off64_t (*s_ftello64)(FILE *);
static int (*s_ferror)(FILE *);
static int (*s_fflush)(FILE *);
static int (*s_feof)(FILE *);
static char * (*s_fgets)(char *, int, FILE *);
static int (*s_fgetc)(FILE *);

static void stat64to32(struct stat *st32, const struct stat64 *st64)
{
	memset(st32, 0, sizeof(struct stat) );

	st32->st_dev = st64->st_dev;
	st32->st_ino = st64->st_ino;
	st32->st_mode = st64->st_mode;
	st32->st_nlink = st64->st_nlink;
	st32->st_uid = st64->st_uid;
	st32->st_gid = st64->st_gid;
	st32->st_rdev = st64->st_rdev;
	st32->st_size = st64->st_size;
	st32->st_blksize = st64->st_blksize;
	st32->st_blocks = st64->st_blocks;
	st32->st_atime = st64->st_atime;
	st32->st_mtime = st64->st_mtime;
	st32->st_ctime = st64->st_ctime;
}

static int initIfNeeded()
{

	char *em;

	sigset_t block_set;
	
	
	/* we do not want to be interrupted */
	sigemptyset(&block_set);
	sigaddset(&block_set, SIGALRM);
	sigprocmask(SIG_BLOCK, &block_set, NULL);

	m_lock(&gLock);

	if(handle != NULL) {		
		m_unlock(&gLock);
/*		dc_debug(DC_TRACE, "System IO already initialized.");		*/
		
		/* restore signal handling */
		sigprocmask(SIG_UNBLOCK, &block_set, NULL);
		return 0;
	}

/*	dc_debug(DC_IO, "Initializing System IO."); */
	if( getenv("DCACHE_IOLIB") != NULL ) {
		libname = getenv("DCACHE_IOLIB");
	}
	if( libname != NULL ) {
		handle = dlopen( libname, RTLD_NOW | RTLD_GLOBAL);
		if(handle == NULL) {		
			m_unlock(&gLock);
/*			dc_debug(DC_ERROR, "Failed to initialize System IO: (%s).", dlerror()); */
#if 0
			perror(dlerror() );
#endif
			/* restore signal handling */
			sigprocmask(SIG_UNBLOCK, &block_set, NULL); 
			return -1;
		}
	}
	#ifdef RTLD_NEXT
	else{
		/* try to use other dynamic libraries to get requaried functions */
		
		handle = RTLD_NEXT;
	}
	#endif

	s_open = (int (*)(const char * , int, ... ))dlsym(handle, OPEN_SYM);
	s_read = (ssize_t (*)(int , void *, size_t ))dlsym(handle, READ_SYM);
	s_readv = (ssize_t (*)(int, const struct iovec *vector, int count))dlsym(handle, READV_SYM);
	s_pread = (ssize_t (*)(int , void *, size_t, off_t ))dlsym(handle, PREAD_SYM);
	s_pread64 = (ssize_t (*)(int , void *, size_t, off64_t ))dlsym(handle, PREAD64_SYM);
	s_write = (ssize_t (*)(int ,const void *, size_t ))dlsym(handle, WRITE_SYM);
	s_writev = (ssize_t (*)(int, const struct iovec *vector, int count))dlsym(handle, WRITEV_SYM);
	s_pwrite = (ssize_t (*)(int ,const void *, size_t, off_t ))dlsym(handle, PWRITE_SYM);	
	s_pwrite64= (ssize_t (*)(int ,const void *, size_t, off64_t ))dlsym(handle, PWRITE64_SYM);
	s_lseek64 = (off64_t (*)(int ,off64_t, int ))dlsym(handle, LSEEK64_SYM);
	s_close = (int (*)(int))dlsym(handle, CLOSE_SYM);
		 
#ifdef _STAT_VER
	s_stat =  (int(*)(int , const char *, struct stat *))dlsym(handle,STAT64_SYM);
	s_stat64 =  (int(*)(int , const char *, struct stat64 *))dlsym(handle,STAT64_SYM);	
	s_lstat =  (int(*)(int , const char *, struct stat *))dlsym(handle,LSTAT_SYM);
	s_lstat64 =  (int(*)(int , const char *, struct stat64 *))dlsym(handle,LSTAT64_SYM);
	s_fstat =  (int(*)(int, int, struct stat *))dlsym(handle, FSTAT_SYM);
	s_fstat64 =  (int(*)(int, int, struct stat64 *))dlsym(handle, FSTAT64_SYM);
#else
	s_stat =  (int(*)(const char *, struct stat *))dlsym(handle,STAT64_SYM);
	s_stat64 =  (int(*)( const char *, struct stat64 *))dlsym(handle,STAT64_SYM);	
	s_lstat =  (int(*)(const char *, struct stat *))dlsym(handle,LSTAT_SYM);
	s_lstat64 =  (int(*)(const char *, struct stat64 *))dlsym(handle,LSTAT64_SYM);
	s_fstat =  (int(*)(int, struct stat *))dlsym(handle, FSTAT_SYM);
	s_fstat64 =  (int(*)(int, struct stat64 *))dlsym(handle, FSTAT64_SYM);
#endif
	s_fsync = (int (*)(int))dlsym(handle, FSYNC_SYM);
	
	s_dup = (int (*)(int))dlsym(handle, DUP_SYM);

	s_opendir = (DIR *(*)(const char *))dlsym(handle, OPENDIR_SYM);
	s_closedir = (int (*)(DIR *))dlsym(handle, CLOSEDIR_SYM);
	s_readdir = (struct dirent *(*)(DIR *))dlsym(handle, READDIR_SYM);
	s_readdir64 = (struct dirent64 *(*)(DIR *))dlsym(handle, READDIR64_SYM);
	s_telldir = (off_t (*)(DIR *))dlsym(handle, TELLDIR_SYM);
	s_seekdir = (void (*)(DIR *, off_t))dlsym(handle, SEEKDIR_SYM);

	s_unlink = (int (*)(const char *))dlsym(handle, UNLINK_SYM);
	s_rmdir = (int (*)(const char *))dlsym(handle, RMDIR_SYM);
	s_mkdir = (int (*)(const char *, mode_t))dlsym(handle, MKDIR_SYM);
	s_chmod = (int (*)(const char *, mode_t))dlsym(handle, CHMOD_SYM);
	s_chown = (int (*)(const char *, uid_t, gid_t))dlsym(handle, CHOWN_SYM);
	s_access = (int (*)(const char *, int))dlsym(handle, ACCESS_SYM);
	s_rename = (int (*)(const char *, const char *))dlsym(handle, RENAME_SYM);
		
	s_fopen = (FILE * (*)(const char * , const char *))dlsym(handle, "fopen");
	s_fopen64 = (FILE * (*)(const char * , const char *))dlsym(handle, "fopen64");
	s_fdopen = (FILE * (*)(int , const char *))dlsym(handle, "fdopen");
	s_fread = (size_t (*)(void *, size_t, size_t, FILE * ))dlsym(handle, "fread");
	s_fwrite = (size_t (*)(const void *, size_t, size_t, FILE * ))dlsym(handle, "fwrite");
	s_fseeko64 = (int (*)(FILE * , off64_t, int ))dlsym(handle, "fseeko64");
	s_fclose = (int (*)(FILE *))dlsym(handle, "fclose");		 
	s_fflush = (int (*)(FILE *))dlsym(handle, "fflush");
	s_ftello64 = (off64_t (*)(FILE *))dlsym(handle, "ftello64");
	s_feof = (int (*)(FILE *))dlsym(handle, "feof");
	s_ferror = (int (*)(FILE *))dlsym(handle, "ferror");
	s_fgets = (char *(*)(char *, int , FILE *))dlsym(handle, "fgets");
	s_fgetc = (int (*)(FILE *))dlsym(handle, "fgetc");
	
	if( (s_open == NULL) || (s_read == NULL) ||
		(s_pread == NULL) || (s_write == NULL) || 
		(s_pwrite == NULL) || (s_pread64 == NULL) || (s_pwrite64 == NULL) ||
		(s_lseek64 == NULL) || (s_close == NULL) ||
		(s_stat == NULL) || (s_fstat == NULL ) || (s_fsync == NULL) || 
		(s_stat64 == NULL) || (s_fstat64 == NULL ) || 
		(s_lstat == NULL) || (s_lstat64 == NULL ) || 
		(s_dup == NULL) || (s_opendir == NULL) || (s_closedir == NULL) ||
		(s_readdir == NULL) || (s_readdir64 ==  NULL) ||
		(s_telldir == NULL) || (s_seekdir == NULL) || 
		(s_unlink == NULL ) || (s_rmdir == NULL ) || 
		(s_mkdir == NULL ) || (s_chmod == NULL ) || (s_access == NULL )  || 
		(s_chown == NULL ) ) {
		
		/* try to write error message it's possible */
		if( s_write != NULL ) {
			em = dlerror();
			if(em != NULL ) {		
				s_write(2, em, strlen(em) );
			}
		}
		
/*		dc_debug(DC_ERROR, "Failed to initialize System IO: (%s).", dlerror()); */
		dlclose(handle);
		handle = NULL;
		m_unlock(&gLock);

#if 0
		perror( dlerror() );

		fprintf(stderr, "open 0x%x\n", sOpen);
		fprintf(stderr, "read 0x%x\n", sRead);
		fprintf(stderr, "write 0x%x\n", sWrite);
		fprintf(stderr, "pread 0x%x\n", sPread);
		fprintf(stderr, "pwrite 0x%x\n", sPwrite);
		fprintf(stderr, "lseek 0x%x\n", sLseek);
		fprintf(stderr, "close 0x%x\n", sClose);
		fprintf(stderr, "stat 0x%x\n", sStat);
		fprintf(stderr, "stat64 0x%x\n", sStat64);
		fprintf(stderr, "fstat 0x%x\n", sFstat);
		fprintf(stderr, "fstat64 0x%x\n", sFstat64);
		fprintf(stderr, "lstat 0x%x\n", sLstat);
		fprintf(stderr, "lstat64 0x%x\n", sLstat64);
		fprintf(stderr, "dup 0x%x\n", sDup);
		fprintf(stderr, "fsync 0x%x\n", sFsync);
		fprintf(stderr, "opendir 0x%x\n", sOpendir);
		fprintf(stderr, "readdir 0x%x\n", sReaddir);
		fprintf(stderr, "readdir64 0x%x\n", sReaddir64);
		fprintf(stderr, "closedir 0x%x\n", sClosedir);
		fprintf(stderr, "seekdir 0x%x\n", sSeekdir);						
		fprintf(stderr, "telldir 0x%x\n", sTelldir);		
		fflush(stderr);
#endif
		/* restore signal handling */
		sigprocmask(SIG_UNBLOCK, &block_set, NULL);

		return -17;

	
	}
	
	m_unlock(&gLock);

	/* restore signal handling */
	sigprocmask(SIG_UNBLOCK, &block_set, NULL); 
	
	return 0;

}

int
system_open(const char *fd, int flags, mode_t mode)
{
	return initIfNeeded() == 0 ? s_open(fd, flags, mode) : -1;
}

int
system_read(int fd, void *buf, size_t buflen)
{
	return initIfNeeded() == 0 ? s_read( fd, buf, buflen) : -1;
}

int
system_readv(int fd, const struct iovec *vector, int count)
{
	return initIfNeeded() == 0 ? s_readv( fd, vector, count) : -1;
}

int system_pread(int fd, void *buf, size_t buflen, off_t offset)
{
	return initIfNeeded() == 0 ? s_pread( fd, buf, buflen, offset) : -1;
}

int system_pread64(int fd, void *buf, size_t buflen, off64_t offset)
{
	return initIfNeeded() == 0 ? s_pread64( fd, buf, buflen, offset) : -1;
}

int system_write(int fd, const void *buf, size_t buflen)
{
	return initIfNeeded() == 0 ? s_write( fd, buf, buflen) : -1;
}

int
system_writev(int fd, const struct iovec *vector, int count)
{
	return initIfNeeded() == 0 ? s_writev( fd, vector, count) : -1;
}

int system_pwrite(int fd, void *buf, size_t buflen, off_t offset)
{
	return initIfNeeded() == 0 ? s_pwrite( fd, buf, buflen, offset) : -1;
}

int system_pwrite64(int fd, void *buf, size_t buflen, off64_t offset)
{
	return initIfNeeded() == 0 ? s_pwrite64( fd, buf, buflen, offset) : -1;
}

int system_close(int fd)
{
	return initIfNeeded() == 0 ? s_close(fd) : -1;
}


off64_t
system_lseek64(int fd, off64_t offset, int whence)
{
	return initIfNeeded() == 0 ? s_lseek64(fd, offset, whence) : -1;
}

#ifdef _STAT_VER
int system_fstat( int fd, struct stat *buf)
{
	struct stat64 s;
	int rc;
	
	if ( initIfNeeded() != 0 ) { 
		return -1;
	} 

	rc = s_fstat64(_STAT_VER, fd, &s);

	stat64to32(buf, &s);
	return rc;

}

int system_stat64( const char *path, struct stat64 *buf)
{
	return initIfNeeded() == 0 ? s_stat64(_STAT_VER, path, buf) : -1;
}


int system_fstat64( int fd, struct stat64 *buf)
{
	return initIfNeeded() == 0 ? s_fstat64(_STAT_VER, fd, buf) : -1;
}

int system_stat( const char *path, struct stat *buf)
{
	struct stat64 s;
	int rc;
	
	if ( initIfNeeded() != 0 ) { 
		return -1;
	} 

	rc = s_stat64(_STAT_VER, path, &s);

	stat64to32(buf, &s);
	return rc;
}


int system_lstat( const char *path, struct stat *buf)
{
	struct stat64 s;
	int rc;
	if ( initIfNeeded() != 0 ) {
		return -1;
	} 

	rc = s_lstat64(_STAT_VER, path, &s);

	stat64to32(buf, &s);
	return rc;
}

int system_lstat64( const char *path, struct stat64 *buf)
{
	return initIfNeeded() == 0 ? s_lstat64(_STAT_VER, path, buf) : -1;
}
#else
int system_fstat( int fd, struct stat *buf)
{
	struct stat64 s;
	int rc;
	
	if ( initIfNeeded() != 0 ) { 
		return -1;
	} 

	rc = s_fstat64(fd, &s);

	stat64to32(buf, &s);
	return rc;

}

int system_stat64( const char *path, struct stat64 *buf)
{
	return initIfNeeded() == 0 ? s_stat64(path, buf) : -1;
}


int system_fstat64( int fd, struct stat64 *buf)
{
	return initIfNeeded() == 0 ? s_fstat64(fd, buf) : -1;
}

int system_stat( const char *path, struct stat *buf)
{
	struct stat64 s;
	int rc;
	
	if ( initIfNeeded() != 0 ) { 
		return -1;
	} 

	rc = s_stat64(path, &s);

	stat64to32(buf, &s);
	return rc;
}


int system_lstat( const char *path, struct stat *buf)
{
	struct stat64 s;
	int rc;
	if ( initIfNeeded() != 0 ) {
		return -1;
	} 

	rc = s_lstat64(path, &s);

	stat64to32(buf, &s);
	return rc;
}

int system_lstat64( const char *path, struct stat64 *buf)
{
	return initIfNeeded() == 0 ? s_lstat64(path, buf) : -1;
}
#endif


int system_fsync(int fd)
{
	return initIfNeeded() == 0 ? s_fsync(fd) : -1;
}

int system_dup(int fd)
{
	return initIfNeeded() == 0 ? s_dup(fd) : -1;
}

DIR * system_opendir(const char *path)
{
	return initIfNeeded() == 0? s_opendir(path) : NULL;
}

int system_closedir( DIR *dir )
{
	return initIfNeeded() == 0? s_closedir(dir) : -1;
}

struct dirent * system_readdir(DIR *dir)
{
	return initIfNeeded() == 0? s_readdir(dir) : NULL;
}

struct dirent64 * system_readdir64(DIR *dir)
{
	return initIfNeeded() == 0? s_readdir64(dir) : NULL;
}

off_t system_telldir( DIR *dir)
{
	return initIfNeeded() == 0?  s_telldir(dir) : -1;
}

void system_seekdir( DIR *dir, off_t offset)
{
	if ( initIfNeeded() == 0 ) {
		s_seekdir(dir, offset );
	}
}


FILE * system_fopen( const char *path, const char *mode)
{
	return initIfNeeded() == 0 ? s_fopen(path, mode) : NULL;
}

FILE * system_fopen64( const char *path, const char *mode)
{
	return initIfNeeded() == 0 ? s_fopen64(path, mode) : NULL;
}

FILE * system_fdopen( int fd, const char *mode)
{
	return initIfNeeded() == 0 ? s_fdopen(fd, mode) : NULL;
}

size_t system_fread( void *buf, size_t i , size_t n, FILE *stream)
{
	return initIfNeeded() == 0 ? s_fread(buf, i, n, stream) : 0;
}

size_t system_fwrite( const void *buf, size_t i , size_t n, FILE *stream)
{
	return initIfNeeded() == 0 ? s_fwrite(buf, i, n, stream) : 0;
}

int system_fclose(FILE *stream)
{
	return initIfNeeded() == 0 ? s_fclose(stream) : -1;
}

int system_feof(FILE *stream)
{
	return initIfNeeded() == 0 ? s_feof(stream) : -1;
}

int system_ferror(FILE *stream)
{
	return initIfNeeded() == 0 ? s_ferror(stream) : -1;
}

int system_fflush(FILE *stream)
{
	return initIfNeeded() == 0 ? s_fflush(stream) : -1;
}

long system_ftello64(FILE *stream)
{
	return initIfNeeded() == 0 ? s_ftello64(stream) : -1;
}

int system_fseeko64(FILE *stream, off64_t offset, int w)
{
	return initIfNeeded() == 0 ? s_fseeko64(stream, offset, w) : -1;
}

char * system_fgets( char *s, int size, FILE *stream)
{
	return initIfNeeded() == 0 ? s_fgets(s, size, stream) : NULL;
}

int system_fgetc(FILE *stream)
{
	return initIfNeeded() == 0 ? s_fgetc(stream) : -1;
}

int system_unlink(const char *path)
{
	return initIfNeeded() == 0 ? s_unlink(path) : -1;
}

int system_rmdir(const char *path)
{
	return initIfNeeded() == 0 ? s_rmdir(path) : -1;
}

int system_mkdir(const char *path, mode_t mode)
{
	return initIfNeeded() == 0 ? s_mkdir(path, mode) : -1;
}

int system_chmod(const char *path, mode_t mode)
{
        return initIfNeeded() == 0 ? s_chmod(path, mode) : -1;
}

int system_chown(const char *path, uid_t uid, gid_t gid)
{
        return initIfNeeded() == 0 ? s_chown(path, uid, gid) : -1;
}

int system_access(const char *path, int mode)
{
        return initIfNeeded() == 0 ? s_access(path, mode) : -1;
}


int system_rename(const char *oldPath, const char *newPath)
{
        return initIfNeeded() == 0 ? s_rename(oldPath, newPath) : -1;
}

#endif /* LIBC_SYSCALLS */
