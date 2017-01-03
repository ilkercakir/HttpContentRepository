package ContentRepositoryFN;

import java.io.*;
import java.util.*;
import java.net.*;
import java.security.PrivilegedAction;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.constants.*;
import com.filenet.api.core.*;
import com.filenet.api.exception.*;
import com.filenet.api.util.UserContext;

public class dispatcher extends HttpServlet
{
	private ResourceBundle rb;
	private String boundary;
	private int buffersize = 0;
	private String noteEncoding = "";
	private Connection conn;
        private ObjectStore os;
	private Document doc;

	public dispatcher()
	{
		rb = ResourceBundle.getBundle("ContentRepositoryFN.FileNetRepository");
		buffersize = Integer.valueOf(rb.getString("ContentRepositoryFN.BUFFERSIZE"));
		boundary = "iLkErÇaKıR"; // Boundary string should be chosen such that it may not exist in the content
		noteEncoding = "ISO-8859-1";
	}

	public void setBoundary(String newBoundary)
	{
		boundary = newBoundary;
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		int ret = processCommand(request, response); // ret = error return code
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		doGet(request,response);
	}

	public void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		doGet(request,response);
	}

// Document Component Interface
	public int dispatcher_info(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;

		String pathFOB = "";
		String ID = "";
		int pageCount = 0;

		DataOutputStream dos;
		File inputFile = null;
		FileInputStream fis = null;
		byte[] b;
		int bytesread = 0;
		ServletInputStream sis;
		FileOutputStream fos = null;

		FileNetRepository fnc = null;

		int i;
		PrintWriter pw = null;

		dcl = new dispatcherCommandLine(request, rb);

//		response.setContentType("multipart/form-data; boundary=" + boundary);
		response.setHeader("X-numberComps", Integer.toString(pageCount));
		response.setHeader("X-contentRep", dcl.contRep);
		response.setHeader("X-docStatus", "online");

		fnc = new FileNetRepository(dcl.contRep);
		pageCount = fnc.getComponentInfo(dcl.contRep, dcl.docId);

		try
		{
			pw = response.getWriter();
			for(i=0;i<pageCount;i++)
			{
				pw.println("--" + boundary);
				pw.println("Content-Type: application/octet-stream");
				pw.println("Content-Disposition: " + rb.getString("ContentRepositoryFN.DISPATCHER.CONTENT_DISPOSITION") + ";filename=" + dcl.docId + "_" + fnc.components.get(i));
				pw.println("Content-Length: " + 0);
				pw.println("X-compId: " + "data" + fnc.components.get(i));
				pw.println("X-Content-Length: " + 0);
				pw.println("X-compStatus: " + "online");
				pw.println();
			}
			pw.println("--" + boundary + "--");
			pw.flush();
			pw.close();
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}

		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_get(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;

		String pathFnet = "";
		String ID = "";
		int pageCount = 0;

		DataOutputStream dos;
		InputStream fnetis = null;
		byte[] b;
		int bytesread = 0;
		ServletInputStream sis;

		FileNetRepository fnc = null;

		int i = 0;

		dcl = new dispatcherCommandLine(request, rb);

		fnc = new FileNetRepository(dcl.contRep);

		response.setHeader("X-numComps", "1");
		response.setHeader("X-contRep", dcl.contRep);
		response.setHeader("X-docId", dcl.docId);
		response.setHeader("X-compId", dcl.compId);
		try
		{
			dos = new DataOutputStream(response.getOutputStream());
			b = new byte[buffersize];

			String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
			String sn = rb.getString(s);
			s = "ContentRepositoryFN." + sn + ".CE_URI";
			String ce_uri = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".USERID";
			String userid = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".PASSWORD";
			String password = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
			String stanzaname = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
			String objectstorename = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
			String foldername = rb.getString(s);

			conn = Factory.Connection.getConnection(ce_uri);
           		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
            		UserContext.get().pushSubject(subject);
            		try
            		{
               			Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
				os = Factory.ObjectStore.getInstance(dom, objectstorename);
				for(i=Integer.valueOf(dcl.fromOffset);i<=Integer.valueOf(dcl.toOffset);i++)
				{
					pathFnet = fnc.UUID2Path(dcl.contRep, dcl.docId);
					String fullPath = foldername + "/" + pathFnet;
					try
					{
						doc = Factory.Document.getInstance(os, null, fullPath);
						doc.refresh(new String[] {PropertyNames.CONTENT_ELEMENTS});
						fnetis = doc.accessContentStream(0);
						while( (bytesread=fnetis.read(b)) != -1 )
						{
							dos.write(b,0,bytesread);
						}
						fnetis.close();
						}
					catch(Exception ex)
					{
						UserContext.get().popSubject();
						response.sendError(response.SC_BAD_REQUEST);
						return Integer.valueOf(rb.getString("ContentRepositoryFN.RETRIEVE_ERROR"));
					}
				}
				dos.flush();
				dos.close();
            		}
            		finally
            		{
                		UserContext.get().popSubject();
            		}
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_docGet(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_create(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;

		byte[] b;
		int bytesread = 0;
		ServletInputStream sis;
		ByteArrayOutputStream fos;
		ByteArrayInputStream fins;

		FileNetRepository fnc = null;

		boolean rnflag = false;
		String line = null;

		dcl = new dispatcherCommandLine(request, rb);

		b = new byte[buffersize];
		
		fnc = new FileNetRepository(dcl.contRep);

		String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
		String sn = rb.getString(s);
		s = "ContentRepositoryFN." + sn + ".CE_URI";
		String ce_uri = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".USERID";
		String userid = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".PASSWORD";
		String password = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
		String stanzaname = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
		String objectstorename = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
		String foldername = rb.getString(s);

		conn = Factory.Connection.getConnection(ce_uri);
       		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
       		UserContext.get().pushSubject(subject);
		try
		{
 			sis = request.getInputStream();
			readBoundary(request);
			// read header
			while ((bytesread = sis.readLine(b, 0, b.length)) != -1)
			{
				line = new String(b, 0, bytesread, "ISO-8859-1");
				if (line.toLowerCase().startsWith("content-"))
				{
				}
				else if (line.toLowerCase().startsWith("x-compid"))
				{
				}
				else if (line.startsWith("\r\n"))
				{
					break;
				}
			}
			// read content
			fos = new ByteArrayOutputStream();
			while ((bytesread = sis.readLine(b, 0, b.length)) != -1)
			{
				if (bytesread > 2 && b[0] == '-' && b[1] == '-')
				{
					line = new String(b, 0, bytesread, "ISO-8859-1");
					if (line.startsWith("--" + boundary))
						break;
				}

				if (rnflag)
				{
					fos.write("\r\n".getBytes());
					rnflag = false;
				}

				if (bytesread >= 2 && b[bytesread - 2] == '\r' && b[bytesread - 1] == '\n')
				{
					fos.write(b, 0, bytesread - 2);
					rnflag = true;
				}
				else
				{
					fos.write(b, 0, bytesread);
				}
			}
			fins = new ByteArrayInputStream(fos.toByteArray());

			try
			{
               			Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
				os = Factory.ObjectStore.getInstance(dom, objectstorename);
				doc = Factory.Document.createInstance(os, null);
				ContentElementList objContentElementList = Factory.ContentElement.createList();
                        	ContentTransfer objContentTransfer = Factory.ContentTransfer.createInstance();

				objContentTransfer.setCaptureSource(fins);
				objContentTransfer.set_ContentType("application/octet-stream");
				objContentTransfer.set_RetrievalName(dcl.docId);
				objContentElementList.add(objContentTransfer);

				doc.set_ContentElements(objContentElementList);
				doc.getProperties().putValue("DocumentTitle", dcl.docId);
                        	doc.set_MimeType("application/octet-stream");
                        	doc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
                        	doc.save(RefreshMode.REFRESH);

				String folderPath = foldername + "/" + fnc.getFolderFromUUID(dcl.contRep, dcl.docId);
				Folder folder = Factory.Folder.getInstance(os, null, folderPath);
        			ReferentialContainmentRelationship rcr = folder.file(doc, AutoUniqueName.AUTO_UNIQUE, dcl.docId, DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
        			rcr.save(RefreshMode.REFRESH);
				//System.out.println("Saved " + dcl.docId + " to " + folderPath);
			}
			catch (Exception ex)
			{
				UserContext.get().popSubject();
				response.sendError(response.SC_BAD_REQUEST);
				return Integer.valueOf(rb.getString("ContentRepositoryFN.STORE_ERROR"));
			}
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
            	finally
            	{
                	UserContext.get().popSubject();
            	}

		dcl.compId = "" + fnc.getNextComponentId(dcl.contRep, dcl.docId);
		fnc.createComponent(dcl.contRep, dcl.docId, Integer.valueOf(dcl.compId), "");

		response.setHeader("X-contRep", dcl.contRep);
		response.setHeader("X-docId", dcl.docId);

		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_mCreate(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_append(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_update(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_delete(HttpServletRequest request, HttpServletResponse response)
	{
		String fullPath = "";
		String pathFnet = "";
		int i = 0;
		dispatcherCommandLine dcl = null;
		FileNetRepository fnc = null;

		dcl = new dispatcherCommandLine(request, rb);

		fnc = new FileNetRepository(dcl.contRep);

		String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
		String sn = rb.getString(s);
		s = "ContentRepositoryFN." + sn + ".CE_URI";
		String ce_uri = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".USERID";
		String userid = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".PASSWORD";
		String password = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
		String stanzaname = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
		String objectstorename = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
		String foldername = rb.getString(s);

		conn = Factory.Connection.getConnection(ce_uri);
       		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
       		UserContext.get().pushSubject(subject);
		try
		{
 			i = fnc.getComponentInfo(dcl.contRep, dcl.docId);
			for(;i>0;i--)
			{
				try
				{
               				Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
					os = Factory.ObjectStore.getInstance(dom, objectstorename);

					pathFnet = fnc.UUID2Path(dcl.contRep, dcl.docId);
					fullPath = foldername + "/" + pathFnet;
					doc = Factory.Document.getInstance(os, null, fullPath);
					doc.delete();
					doc.save(RefreshMode.REFRESH);
				}
				catch (Exception ex)
				{
					UserContext.get().popSubject();
					response.sendError(response.SC_BAD_REQUEST);
					return Integer.valueOf(rb.getString("ContentRepositoryFN.DELETE_FAILED"));
				}

				try
				{
					doc = Factory.Document.getInstance(os, null, fullPath + ".txt");
					doc.delete();
					doc.save(RefreshMode.REFRESH);
				}
				catch(Exception exc)
				{
					//UserContext.get().popSubject();
					//response.sendError(response.SC_BAD_REQUEST);
					//return Integer.valueOf(rb.getString("ContentRepositoryFN.DELETE_FAILED"));
				}
			}
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
            	finally
            	{
                	UserContext.get().popSubject();
            	}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_search(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_attrSearch(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_putCert(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_adminContRep(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			response.sendError(response.SC_METHOD_NOT_ALLOWED);
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_serverInfo(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;

		String pathFOB = "";
		int pageCount = 0;

		DataOutputStream dos;
		File inputFile = null;
		FileInputStream fis = null;
		byte[] b;
		int bytesread = 0;
		ServletInputStream sis;
		FileOutputStream fos = null;

		FileNetRepository fnc = null;

		int ret = 0;
		String status = "";
		String line = "";
		PrintWriter pw = null;

		dcl = new dispatcherCommandLine(request, rb);

		if (dcl.resultAs.equals("ascii"))
		{
			// Content Server
			line = line + "serverStatus=\"";
			fnc = new FileNetRepository(dcl.contRep);
			status = fnc.getStatus();
			line = line + status + "\";";

			line = line + "serverVendorId=\"";
			line = line + "YM";
			line = line + "\";";

			Calendar cal = Calendar.getInstance();

			line = line + "serverTime=\"";
			line = line + cal.getTime();
			line = line + "\";";

			line = line + "serverDate=\"";
			String sday = "00" + Integer.toString(cal.get(Calendar.DAY_OF_MONTH));
			sday = sday.substring(sday.length()-2);
			String smon = "00" + Integer.toString(cal.get(Calendar.MONTH)+1);
			smon = smon.substring(smon.length()-2);
			String datum = sday + "." + smon + "." + Integer.toString(cal.get(Calendar.YEAR));
			line = line + datum + "\";";

			line = line + "serverErrorDescription=\"";
			line = line + "\";";

			line = line + "pVersion=\"";
			line = line + dcl.pVersion;
			line = line + "\"";
			try
			{
				pw = response.getWriter();
				pw.println(line);
			}
			catch (java.io.IOException ioex)
			{
				return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
			}

			// Content Repository
			line = "";

			line = line + "contRep=\"";
			line = line + dcl.contRep;
			line = line + "\";";

			line = line + "contRepDescription=\"";
			line = line + dcl.contRep;
			line = line + "\";";

			line = line + "contRepStatus=\"";
			line = line + status;
			line = line + "\";";

			line = line + "contRepStatusDescription=\"";
			line = line + status;
			line = line + "\";";

			try
			{
				pw = response.getWriter();
				pw.println(line);
			}
			catch (java.io.IOException ioex)
			{
				return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
			}

			pw.flush();
			pw.close();
		}
		else
		{
			try
			{
				response.sendError(response.SC_METHOD_NOT_ALLOWED);
			}
			catch (java.io.IOException ioex)
			{
				return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
			}
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

// Note interface
	public int dispatcher_getNote(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;
		DataOutputStream dos;
		InputStream fnetis = null;
		String pathFnet = "";
		byte[] b;
		int bytesread = 0;
		int i = 0;
		FileNetRepository fnc = null;
		String docNote = "";

		b = new byte[buffersize];
		dcl = new dispatcherCommandLine(request, rb);

		fnc = new FileNetRepository(dcl.contRep);

		String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
		String sn = rb.getString(s);
		s = "ContentRepositoryFN." + sn + ".CE_URI";
		String ce_uri = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".USERID";
		String userid = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".PASSWORD";
		String password = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
		String stanzaname = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
		String objectstorename = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
		String foldername = rb.getString(s);

		conn = Factory.Connection.getConnection(ce_uri);
           	Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
            	UserContext.get().pushSubject(subject);

		response.setHeader("X-numComps", "1");
		response.setHeader("X-contRep", dcl.contRep);
		response.setHeader("X-docId", dcl.docId);
		response.setHeader("X-compId", dcl.compId);
		try
		{
            		Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
			os = Factory.ObjectStore.getInstance(dom, objectstorename);

			dos = new DataOutputStream(response.getOutputStream());
			for(i=Integer.valueOf(dcl.fromOffset);i<=Integer.valueOf(dcl.toOffset);i++)
			{
				pathFnet = fnc.UUID2Path(dcl.contRep, dcl.docId);
				String fullPath = foldername + "/" + pathFnet + ".txt";
				doc = Factory.Document.getInstance(os, null, fullPath);
				try
				{
					doc.refresh(new String[] {PropertyNames.CONTENT_ELEMENTS});
					fnetis = doc.accessContentStream(0);
					while( (bytesread=fnetis.read(b)) != -1 )
					{
						dos.write(b,0,bytesread);
					}
					fnetis.close();
				}
				catch (Exception ex)
				{	
				}
			}
			dos.flush();
			dos.close();
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
            	finally
       		{
                	UserContext.get().popSubject();
            	}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_appendNote(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;
		byte[] b;
		ServletInputStream sis;
		FileNetRepository fnc = null;
		boolean rnflag = false;
		String line = null;
		String docNote = "";
		int bytesread = 0;
		ByteArrayInputStream fins;

		b = new byte[buffersize];

		dcl = new dispatcherCommandLine(request, rb);

		fnc = new FileNetRepository(dcl.contRep);

		String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
		String sn = rb.getString(s);
		s = "ContentRepositoryFN." + sn + ".CE_URI";
		String ce_uri = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".USERID";
		String userid = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".PASSWORD";
		String password = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
		String stanzaname = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
		String objectstorename = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
		String foldername = rb.getString(s);

		conn = Factory.Connection.getConnection(ce_uri);
       		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
       		UserContext.get().pushSubject(subject);
		try
		{
 			sis = request.getInputStream();
			// read content
			while ((bytesread = sis.readLine(b, 0, b.length)) != -1)
			{
				if (bytesread > 2 && b[0] == '-' && b[1] == '-')
				{
					line = new String(b, 0, bytesread, noteEncoding);
					if (line.startsWith("--" + boundary))
						break;
				}

				if (rnflag)
				{
					docNote += "\r\n";
					rnflag = false;
				}

				if (bytesread >= 2 && b[bytesread - 2] == '\r' && b[bytesread - 1] == '\n')
				{
					docNote += new String(b, 0, bytesread-2, noteEncoding);
					rnflag = true;
				}
				else
				{
					docNote += new String(b, 0, bytesread, noteEncoding);
				}
			}
			fins = new ByteArrayInputStream(docNote.getBytes());

			try
			{
         	      		Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
				os = Factory.ObjectStore.getInstance(dom, objectstorename);
				doc = Factory.Document.createInstance(os, null);
				ContentElementList objContentElementList = Factory.ContentElement.createList();
        	                ContentTransfer objContentTransfer = Factory.ContentTransfer.createInstance();

				objContentTransfer.setCaptureSource(fins);
				objContentTransfer.set_ContentType("text/plain");
				objContentTransfer.set_RetrievalName(dcl.docId + ".txt");
				objContentElementList.add(objContentTransfer);

				doc.set_ContentElements(objContentElementList);
				doc.getProperties().putValue("DocumentTitle", dcl.docId + ".txt");
                	        doc.set_MimeType("text/plain");
                        	doc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
	                        doc.save(RefreshMode.REFRESH);

				String folderPath = foldername + "/" + fnc.getFolderFromUUID(dcl.contRep, dcl.docId);
				Folder folder = Factory.Folder.getInstance(os, null, folderPath);
        			ReferentialContainmentRelationship rcr = folder.file(doc, AutoUniqueName.NOT_AUTO_UNIQUE, dcl.docId + ".txt", DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
        			rcr.save(RefreshMode.REFRESH);
				//System.out.println("Saved " + dcl.docId + " note to " + folderPath);
			}
			catch (Exception ex)
			{
				UserContext.get().popSubject();
				response.sendError(response.SC_BAD_REQUEST);
				return Integer.valueOf(rb.getString("ContentRepositoryFN.STORE_ERROR"));
			}
		}
		catch (java.io.FileNotFoundException fnfx)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.FILE_NOT_FOUND_EXCEPTION"));
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
            	finally
            	{
                	UserContext.get().popSubject();
            	}
		fnc.appendNoteText(dcl.contRep, dcl.docId, docNote);

		response.setHeader("X-contRep", dcl.contRep);
		response.setHeader("X-docId", dcl.docId);

		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_updateNote(HttpServletRequest request, HttpServletResponse response)
	{
		dispatcherCommandLine dcl = null;

		byte[] b;
		ServletInputStream sis;
		ByteArrayInputStream fins;
		FileNetRepository fnc = null;
		boolean rnflag = false;
		String line = null;
		String docNote = "";
		int bytesread = 0;

		b = new byte[buffersize];

		dcl = new dispatcherCommandLine(request, rb);

		fnc = new FileNetRepository(dcl.contRep);

		String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
		String sn = rb.getString(s);
		s = "ContentRepositoryFN." + sn + ".CE_URI";
		String ce_uri = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".USERID";
		String userid = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".PASSWORD";
		String password = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
		String stanzaname = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
		String objectstorename = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
		String foldername = rb.getString(s);

		conn = Factory.Connection.getConnection(ce_uri);
       		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
       		UserContext.get().pushSubject(subject);
		try
		{
 			sis = request.getInputStream();
			// read content
			while ((bytesread = sis.readLine(b, 0, b.length)) != -1)
			{
				if (bytesread > 2 && b[0] == '-' && b[1] == '-')
				{
					line = new String(b, 0, bytesread, noteEncoding);
					if (line.startsWith("--" + boundary))
						break;
				}

				if (rnflag)
				{
					docNote += "\r\n";
					rnflag = false;
				}

				if (bytesread >= 2 && b[bytesread - 2] == '\r' && b[bytesread - 1] == '\n')
				{
					docNote += new String(b, 0, bytesread-2, noteEncoding);
					rnflag = true;
				}
				else
				{
					docNote += new String(b, 0, bytesread, noteEncoding);
				}
			}
			fins = new ByteArrayInputStream(docNote.getBytes());

			try
			{
                		Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
				os = Factory.ObjectStore.getInstance(dom, objectstorename);
				doc = Factory.Document.createInstance(os, null);
				ContentElementList objContentElementList = Factory.ContentElement.createList();
                        	ContentTransfer objContentTransfer = Factory.ContentTransfer.createInstance();

				objContentTransfer.setCaptureSource(fins);
				objContentTransfer.set_ContentType("text/plain");
				objContentTransfer.set_RetrievalName(dcl.docId + ".txt");
				objContentElementList.add(objContentTransfer);

				doc.set_ContentElements(objContentElementList);
				doc.getProperties().putValue("DocumentTitle", dcl.docId + ".txt");
                	        doc.set_MimeType("text/plain");
                        	doc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
	                        doc.save(RefreshMode.REFRESH);

				String folderPath = foldername + "/" + fnc.getFolderFromUUID(dcl.contRep, dcl.docId);
				Folder folder = Factory.Folder.getInstance(os, null, folderPath);
        			ReferentialContainmentRelationship rcr = folder.file(doc, AutoUniqueName.NOT_AUTO_UNIQUE, dcl.docId + ".txt", DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
        			rcr.save(RefreshMode.REFRESH);
				//System.out.println("Saved " + dcl.docId + " note to " + folderPath);
			}
			catch (Exception ex)
			{
				UserContext.get().popSubject();
				response.sendError(response.SC_BAD_REQUEST);
				return Integer.valueOf(rb.getString("ContentRepositoryFN.STORE_ERROR"));
			}
		}
		catch (java.io.FileNotFoundException fnfx)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.FILE_NOT_FOUND_EXCEPTION"));
		}
		catch (java.io.IOException ioex)
		{
			return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		}
            	finally
            	{
                	UserContext.get().popSubject();
            	}
		fnc.createNote(dcl.contRep, dcl.docId, docNote);

		response.setHeader("X-contRep", dcl.contRep);
		response.setHeader("X-docId", dcl.docId);

		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

	public int dispatcher_deleteNote(HttpServletRequest request, HttpServletResponse response)
	{
		String pathFnet = "";
		int i = 0;
		dispatcherCommandLine dcl = null;
		FileNetRepository fnc = null;

		dcl = new dispatcherCommandLine(request, rb);

		fnc = new FileNetRepository(dcl.contRep);

		String s = "ContentRepositoryFN.CONTREP." + dcl.contRep;
		String sn = rb.getString(s);
		s = "ContentRepositoryFN." + sn + ".CE_URI";
		String ce_uri = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".USERID";
		String userid = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".PASSWORD";
		String password = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
		String stanzaname = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
		String objectstorename = rb.getString(s);

		s = "ContentRepositoryFN." + sn + ".FOLDER_NAME";
		String foldername = rb.getString(s);

		conn = Factory.Connection.getConnection(ce_uri);
       		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
       		UserContext.get().pushSubject(subject);
		try
		{
 			i = fnc.getComponentInfo(dcl.contRep, dcl.docId);
			for(;i>0;i--)
			{
				try
				{
	               			Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
					os = Factory.ObjectStore.getInstance(dom, objectstorename);

					pathFnet = fnc.UUID2Path(dcl.contRep, dcl.docId);
					String fullPath = foldername + "/" + pathFnet;
					doc = Factory.Document.getInstance(os, null, fullPath + ".txt");
					doc.delete();
					doc.save(RefreshMode.REFRESH);
				}
				catch (Exception ex)
				{
					UserContext.get().popSubject();
					//response.sendError(response.SC_BAD_REQUEST);
					return Integer.valueOf(rb.getString("ContentRepositoryFN.DELETE_FAILED"));
				}
			}
		}
		//catch (java.io.IOException ioex)
		//{
		//	return Integer.valueOf(rb.getString("ContentRepositoryFN.IO_EXCEPTION"));
		//}
            	finally
            	{
                	UserContext.get().popSubject();
            	}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.SUCCESS"));
	}

// Service
	public int processCommand(HttpServletRequest request, HttpServletResponse response)
	{
		String command = "";
		String compType = "";

		compType = request.getParameter("compId");
		if (compType==null)
		{
			compType = "data";
		}
		else
		{
			compType = compType.toLowerCase();
		}

		command = request.getParameter("info");
		if (command != null)
		{
			return dispatcher_info(request, response);
		}
		command = request.getParameter("get");
		if (command != null)
		{
			if (compType.equals("note"))
			{
				return dispatcher_getNote(request, response);
			}
			else
			{
				return dispatcher_get(request, response);
			}
		}
		command = request.getParameter("docGet");
		if (command != null)
		{
			return dispatcher_docGet(request, response);
		}
		command = request.getParameter("create");
		if (command != null)
		{
			return dispatcher_create(request, response);
		}
		command = request.getParameter("mCreate");
		if (command != null)
		{
			return dispatcher_mCreate(request, response);
		}
		command = request.getParameter("append");
		if (command != null)
		{
			if (compType.equals("note"))
			{
				return dispatcher_appendNote(request, response);
			}
			else
			{
				return dispatcher_append(request, response);
			}
		}
		command = request.getParameter("update");
		if (command != null)
		{
			if (compType.equals("note"))
			{
				return dispatcher_updateNote(request, response);
			}
			else
			{
				return dispatcher_update(request, response);
			}
		}
		command = request.getParameter("delete");
		if (command != null)
		{
			if (compType.equals("note"))
			{
				return dispatcher_deleteNote(request, response);
			}
			else
			{
				return dispatcher_delete(request, response);
			}
		}
		command = request.getParameter("search");
		if (command != null)
		{
			return dispatcher_search(request, response);
		}
		command = request.getParameter("attrSearch");
		if (command != null)
		{
			return dispatcher_attrSearch(request, response);
		}
		command = request.getParameter("putCert");
		if (command != null)
		{
			return dispatcher_putCert(request, response);
		}
		command = request.getParameter("serverInfo");
		if (command != null)
		{
			return dispatcher_serverInfo(request, response);
		}
		command = request.getParameter("adminContRep");
		if (command != null)
		{
			return dispatcher_adminContRep(request, response);
		}
		return Integer.valueOf(rb.getString("ContentRepositoryFN.UNKNOWN_COMMAND"));
	}

	private void readBoundary(HttpServletRequest request)
	{
		String contentType = request.getContentType();
		if (contentType == null)
		{
			return; //throw new IllegalArgumentException("Unknown content type.");
		}
		if (!(contentType.toLowerCase().startsWith("multipart/form-data"))) 
		{
			return; //throw new IllegalArgumentException("Unknown content type.");
		}
		int index = contentType.indexOf("boundary=");
		if (index == -1) 
		{
			return; //throw new IllegalArgumentException("No boundary specified.");
		}
		index += 9;
		boundary = contentType.substring(index);
	}

    private static final class CallbackHandler implements javax.security.auth.callback.CallbackHandler
    {
        private String userid;
        private String password;

        public CallbackHandler(String userid, String password)
        {
            this.userid = userid;
            this.password = password;
        }
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException
        {
            for (int i = 0; i < callbacks.length; i++) 
            {
                if (callbacks[i] instanceof TextOutputCallback)
                {
                    //TextOutputCallback toc = (TextOutputCallback)callbacks[i];
                    //System.err.println("JAAS callback: " + toc.getMessage());
                }
                else if (callbacks[i] instanceof NameCallback) 
                {
                    NameCallback nc = (NameCallback)callbacks[i];
                    nc.setName(userid);
                }
                else if (callbacks[i] instanceof PasswordCallback) 
                {
                    PasswordCallback pc = (PasswordCallback)callbacks[i];
                    pc.setPassword(password.toCharArray());
                }
                else
                {
                    throw new UnsupportedCallbackException(callbacks[i], "Unrecognized Callback");
                }
            }
        }
    }
}

// Command Line Processor
class dispatcherCommandLine extends Object
{
	public String command = "";
	public String docId = "";
	public String contRep = "";
	public String pVersion = "";
	public String compId = "";
	public String compType = "";
	public String fromOffset = "";
	public String toOffset = "";
	public String docProt = "";
	public String authId = "";
	private String noAuthId = "";
	public String resultAs = "";
	public String ID = "";

	public dispatcherCommandLine()
	{
	}

	public dispatcherCommandLine(HttpServletRequest request, ResourceBundle res)
	{
		command = request.getParameter("command");
		docId = request.getParameter("docId");
		contRep = request.getParameter("contRep");
		pVersion = request.getParameter("pVersion");
		compId = request.getParameter("compId");
		fromOffset = request.getParameter("fromOffset");
		toOffset = request.getParameter("toOffset");
		docProt = request.getParameter("docProt");
		authId = request.getParameter("authId");
		resultAs = request.getParameter("resultAs");

		if (compId == null)
		{
			if ((fromOffset == null) && (fromOffset == null))
			{
				fromOffset = toOffset = compId = "1";
			}
		}
		else
		{
			if (compId.toLowerCase().equals("note"))
			{
				compId = "0";
				compType = "note";
			}
			else
			{
				compId = Integer.valueOf(compId.toLowerCase().replaceAll("data", "0")).toString();
				if (Integer.valueOf(compId) == 0)
				{
					compId = "1";
				}
				compType = "data";
			}
			fromOffset = toOffset = compId;
		}

		if (contRep == null)
		{
			contRep = res.getString("ContentRepositoryFN.NoContRep");
		}
		else if (contRep.equals(""))
		{
			contRep = res.getString("ContentRepositoryFN.NoContRep");
		}

		if (authId == null)
		{			
			authId = res.getString("ContentRepositoryFN.NoAuthId");
		}
		else
		{
			authId = authId.replace("CN%3D", ""); // authId=CN%3DSID
			authId = authId.replace("CN=", ""); // authId=CN=SID
			authId = authId.substring(0,3);
		}

		if (resultAs == null)
		{
			resultAs = "ascii";
		}
		else if (resultAs.equals(""))
		{
			resultAs = "ascii";
		}
		resultAs = resultAs.toLowerCase();
	}
}

// FileNet Content Repository
class FileNetRepository extends Object
{
        private ResourceBundle rb;
        private int SUBDR;
	private String pathSeparator = "/";
	public ArrayList<String> components;

	public FileNetRepository(String contRep)
	{
		String s;

        	rb = ResourceBundle.getBundle("ContentRepositoryFN.FileNetRepository");
		s = rb.getString("ContentRepositoryFN.CONTREP." + contRep);
		s = "ContentRepositoryFN." + s + ".SUBDR";
		SUBDR = Integer.valueOf(rb.getString(s));
	}

	public void setPathSeparator(String newSeparator)
	{
		pathSeparator = newSeparator;
	}

	public String getStatus()
	{
		return "running FileNet";
	}

	public int UUID2Folder(String UUID)
	{
		int i;
		int folder;
		String myChar;

		folder = 0;
		for(i=0;i<UUID.length();i++)
		{
			myChar = "" + UUID.charAt(i);
			folder += ( ((myChar.compareTo("0")>=0) && (myChar.compareTo("9")<=0)) ? Integer.valueOf(myChar) : ((int)UUID.charAt(i)) - ((int)'A') + 10 );
		}
		return folder;
	}

	public String UUID2Path(String ContRep, String UUID)
	{
		String directory = "" + (UUID2Folder(UUID) % SUBDR);
		String fullpath = directory + pathSeparator + UUID;

		return fullpath;
	}

	public String getFolderFromUUID(String ContRep, String UUID)
	{
		return "" + (UUID2Folder(UUID) % SUBDR);
	}

	public int GetPageCount(String ContRep, String docId)
	{
		int count = 0;

		count = 1;

		return count;
	}

	public int getComponentInfo(String ContRep, String docId)
	{
		int count = 0;

                components = new ArrayList<String>();
		components.add("1");

		count = 1;

		return count;
	}

	public int getNextComponentId(String ContRep, String docId)
	{
		int nextId = 0;
		
		nextId = 1;

		return (nextId + 1);
	}

	public int createComponent(String ContRep, String docId, int compId, String mimeType)
	{
		return 0;
	}

	public int deleteComponent(String ContRep, String docId, int compId)
	{
		return 0;
	}

	public int deleteDocument(String ContRep, String docId)
	{
		return 0;
	}

	public String GetDocFormat(String ContRep, String docId, int compId)
	{
		String docFormat = "";

		return docFormat;	
	}

	public String getNoteText(String ContRep, String docId)
	{
		String str ="";

		return str;
	}

        public int createNote(String ContRep, String docId, String noteText)
	{
		return 0;
	}

	public int appendNoteText(String ContRep, String docId, String noteText)
	{
		return 0;
	}

	public void MoveNext()
	{
	}

	public void MoveFirst()
	{
	}

	public String ProvideValue(String FieldName)
	{
		return "";
	}
}