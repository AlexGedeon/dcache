/*
 *   DCAP - dCache Access Protocol client interface
 *
 *   Copyright (C) 2000,2004 DESY Hamburg DMG-Division.
 *
 *   AUTHOR: Tigran Mkrtchayn (tigran.mkrtchyan@desy.de)
 *
 *   This program can be distributed under the terms of the GNU LGPL.
 *   See the file COPYING.LIB
 *
 */
 
 
/*
 * $Id: dcap_close.c,v 1.8 2004-12-01 14:25:39 tigran Exp $
 */

#include "dcap_shared.h"

extern int dc_real_fsync( struct vsp_node *);


int
dc_close(int fd)
{
	int             res = 0;
	int             tmp;
	int32_t         size;
	int32_t         closemsg[6];
	int             msglen;
	struct vsp_node *node;


#ifdef DC_CALL_TRACE
	showTraceBack();
#endif


	/* nothing wrong ... yet */
	dc_errno = DEOK;

	node = delete_vsp_node(fd);
	if (node == NULL) {
		/* we have not such file descriptor, so lets give a try to system */
		dc_debug(DC_INFO, "Using system native close for [%d].", fd);
		return system_close(fd);
	}


	dc_real_fsync( node );

	if(node->unsafeWrite > 1) {
	
		size = htonl(-1); /* send end of data */
		writen(node->dataFd, (char *) &size, sizeof(size), NULL);
		/* FIXME: error detection missing */

		if (get_fin(node) < 0) {
			dc_debug(DC_ERROR, "dc_close: mover did not FIN the data blocks.");
			res = -1;
		}
	}

	if(node->reference == 0 ) {

		if( (node->sum != NULL) && ( node->sum->isOk == 1 ) ) {
			closemsg[0] = htonl(20);
			closemsg[2] = htonl(12);
			closemsg[3] = htonl(DCAP_DATA_SUM);
			closemsg[4] = htonl(node->sum->type);
			closemsg[5] = htonl(node->sum->sum);

			msglen = 6;
			dc_debug(DC_INFO, "File checksum is: %u", node->sum->sum);
		}else{
			closemsg[0] = htonl(4);
			msglen = 2;			
		}

		closemsg[1] = htonl(IOCMD_CLOSE); /* actual command */

		dc_debug(DC_IO, "Sending CLOSE for fd:%d ID:%d.", node->dataFd, node->queueID);
		dcap_set_alarm(DCAP_IO_TIMEOUT/4);
		tmp = sendDataMessage(node, (char *) closemsg, msglen*sizeof(int32_t), ASCII_OK, NULL);
		/* FIXME: error detection missing */
		if( tmp < 0 ) {
			dc_debug(DC_ERROR, "sendDataMessage failed.");
			/* ignore close errors if file was open for read */
			if(node->flags & O_WRONLY) {
				res = -1;
			}

			if(isIOFailed) {
				isIOFailed = 0;
				/* command line dwon */
				if(!ping_pong(node)) {
					/* remove file descriptor from the list of control lines in use */
					lockMember();
					deleteMemberByValue(node->fd);
					unlockMember();
					pollDelete(node->fd);
					system_close(node->fd);
				}
			}
		}
		dcap_set_alarm(0);

		close_data_socket(node->dataFd);
		deleteQueue(node->queueID);
		
	}



	node_destroy(node);

	return res;
}

/* 
   dc_close2  - same as dc_close, but does not send close command to the
   pool.
*/

int
dc_close2(int fd)
{
	int              res = 0;
	struct vsp_node *node;
	int32_t          size;


#ifdef DC_CALL_TRACE
	showTraceBack();
#endif

	/* nothing wrong ... yet */
	dc_errno = DEOK;

	node = delete_vsp_node(fd);
	if (node == NULL) {
		/* we have not such file descriptor, so lets give a try to system */
		return system_close(fd);
	}


	dc_real_fsync( node );


	if(node->unsafeWrite) {
	
		size = htonl(-1); /* send end of data */
		writen(node->dataFd, (char *) &size, sizeof(size), NULL);
		/* FIXME: error detection missing */

		if (get_fin(node) < 0) {
			dc_debug(DC_ERROR, "dc_close: mover did not FIN the data blocks.");
			res = -1;
		}
	}

	close_data_socket(node->dataFd);	

	deleteQueue(node->queueID);

	m_unlock(&node->mux);
	
	node_destroy(node);

	return res;
}



extern fdList getAllFD();

/*
 * close all open files
 */
void dc_closeAll()
{
	
	int i;
	fdList list = getAllFD(); 
	for( i = 0; i < list.len; i++) {
		dc_close(list.fds[i]);
	}
	
	
	if( list.len > 0 ) {
		free(list.fds);
	}
}
