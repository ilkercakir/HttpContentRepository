package ContentRepositoryFN;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.channels.*;
import java.util.*;
import java.net.*;
import java.security.PrivilegedAction;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.activation.*;

import com.filenet.api.constants.*;
import com.filenet.api.core.*;
import com.filenet.api.collection.*;
import com.filenet.api.property.*;
import com.filenet.api.util.*;
import com.filenet.api.exception.*;

import org.apache.log4j.*; 

public class dispatcherBR extends HttpServlet
{
	private ResourceBundle rb;
	private int buffersize = 0;
	private String boundary = "iLkErCaKiR";
	static Logger logger = Logger.getLogger(dispatcherBR.class);

	public dispatcherBR()
	{
		rb = ResourceBundle.getBundle("ContentRepositoryFN.FileNetRepository");
		buffersize = Integer.valueOf(rb.getString("ContentRepositoryFN.BUFFERSIZE"));

		logger.setLevel(Level.INFO);
		logger.debug(dispatcherBR.class + " init debug messages");
		logger.info(dispatcherBR.class + " init info messages");
		logger.warn(dispatcherBR.class + " init warn messages");
		logger.error(dispatcherBR.class + " init error messages");
		logger.fatal(dispatcherBR.class + " init fatal messages");
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		int bytesread = 0;
		InputStream fnetis = null;
		DataOutputStream dos = null;
		RandomAccessFile raf = null;
		long size = 0;
		String ss = "";
		String sourceMimeType = "";
		int localDocument= 0; // 0=local, 1=external url

		String id = request.getParameter("id");
		logger.debug("id=" + id);
		if (id == null)
		{
			logger.debug("Failed id={} not received");
			return;
		}
		else if (id.equals(""))
		{
			logger.debug("Failed id=empty");
			return;
		}

		try
		{
			String sn = "1"; // Canlı
			String s = "ContentRepositoryFN." + sn + ".CE_URI";
			String ce_uri = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".USERID";
			String userid = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".PASSWORD";
			String password = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".JAAS_STANZA_NAME";
			String stanzaname = rb.getString(s);

			s = "ContentRepositoryFN." + sn + ".OBJECT_STORE_NAME";
			String objectstorename = rb.getString(s);

			Connection conn = Factory.Connection.getConnection(ce_uri);
           		Subject subject = UserContext.createSubject(conn, userid, password, stanzaname);
            		UserContext.get().pushSubject(subject);
            		try
            		{
               			Domain dom = Factory.Domain.getInstance(conn, null); //no R/T
				ObjectStore os = Factory.ObjectStore.getInstance(dom, objectstorename);

				//Document doc = Factory.Document.getInstance(os, null, new Id(id));
				//doc.refresh(new String[] {PropertyNames.CONTENT_ELEMENTS});
				Document doc = Factory.Document.fetchInstance(os, new Id(id), null);
				com.filenet.api.property.Properties props = doc.getProperties();
				Iterator iter = props.iterator();
				while (iter.hasNext())
				{
					Property prop = (Property)iter.next();
					if (prop.getPropertyName().equals(PropertyNames.MIME_TYPE))
					{
               					ss = prop.getStringValue();
						//System.out.println("content-type: " + prop.getStringValue());
						break;
					}
				}

				if (ss.equals("application/x-filenet-external"))
				{
					ContentElementList docContentList = doc.get_ContentElements();
                    			props = null;
					iter = docContentList.iterator();
                    			while (iter.hasNext())
                    			{
                        			ContentElement ce = (ContentElement)iter.next();
                        			props = ce.getProperties();
                        			break;
                    			}
                    			if (props == null)
                    			{
						response.sendError(response.SC_NOT_FOUND);
						return;
                    			}

					iter = props.iterator();
					ss = "";
					while (iter.hasNext())
					{
						Property prop = (Property)iter.next();
						if (prop.getPropertyName().equals(PropertyNames.CONTENT_LOCATION))
						{
               						ss = prop.getStringValue();
							//System.out.println("content-type: " + prop.getStringValue());
							break;
						}
					}
                			String filename = ss;
		                	raf = new RandomAccessFile(filename, "r");
					size = raf.length();
					localDocument = 1;

					logger.debug("Filename : " + filename);
					Path source = Paths.get(filename);
					sourceMimeType = Files.probeContentType(source);
					if (sourceMimeType == null)
					{
						ServletContext servletContext = getServletContext();
						String contextPath = servletContext.getRealPath(File.separator);
						String mimeTypesPath = contextPath + "\\WEB-INF\\lib\\mime-types.default";
						MimetypesFileTypeMap map = new MimetypesFileTypeMap(mimeTypesPath);
						sourceMimeType = map.getContentType(filename);
					}
				}
				else
				{
					ContentElementList docContentList = doc.get_ContentElements();
					Iterator cliter = docContentList.iterator();
					while (cliter.hasNext() )
					{
						ContentTransfer ct = (ContentTransfer) cliter.next();
						size = ct.get_ContentSize().longValue();
						fnetis = ct.accessContentStream();
						break;
					}
					localDocument = 0;

					logger.debug("Success accessContentStream()");
					sourceMimeType = ss;
				}

		                long start = 0;
               			long end = size - 1;
               			long length = size;

				response.setHeader("Access-Control-Allow-Origin", "*");
				response.setHeader("Accept-Ranges", "0-" + size);

				String http_range = request.getHeader("Range");
				if (http_range == null) 
				{
					http_range = request.getHeader("HTTP_RANGE");
					if (http_range == null) 
					{
						http_range = "bytes=" + start+ "-" + end;
					}
				}
				//System.out.println("http_range: " + http_range);
                       		long anotherStart = start;
                       		long anotherEnd = end;
                       		String[] arr_split = http_range.split("=");
                       		String range = arr_split[1];
                      
                       		if (range.indexOf(",") > -1) // Multi byte-range
                       		{
					logger.debug("Success multipart request received " + range);

					dos = new DataOutputStream(response.getOutputStream());
					byte[] b = new byte[buffersize];
					byte[] bh = null;
					response.setContentType("multipart/byteranges; boundary=" + boundary);
					response.setStatus(response.SC_PARTIAL_CONTENT);
					long multi_content_length = 0;

					String[] rangeList = range.split(",");
					for(int rj=0;rj<rangeList.length;rj++)
					{
						start = 0;
               					end = size - 1;
               					length = size;

						range = rangeList[rj];

               					if (range.startsWith("-"))
               					{
                      					// The n-number of the last bytes is requested
                       					anotherStart = size - Long.parseLong(range.substring(1));
                       				}
                       				else
                       				{
                       					arr_split = range.split("-");
                       					anotherStart = Long.parseLong(arr_split[0]);
                       					long temp = 0;
                       					anotherEnd = arr_split.length > 1 && tryParseLong(arr_split[1]) ? Long.parseLong(arr_split[1]) : size;
                       				}
                       				// Check the range and make sure it's treated according to the specs.
                       				// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
                       				// End bytes can not be larger than $end.
                       				anotherEnd = (anotherEnd > end) ? end : anotherEnd;
                       				// Validate the requested range and return an error if it's not correct.
                       				if (anotherStart > anotherEnd || anotherStart > size - 1 || anotherEnd >= size)
                       				{
							//System.out.println("error " + start + "-" + end + "/" + size + " rj[" + rj + "]");
							logger.debug("Failed range not satisfiable " + start + "-" + end + "/" + size + " rj[" + rj + "]");
                       					response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
							response.sendError(response.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
							fnetis.close();
							return;
                       				}
                       				start = anotherStart;
                       				end = anotherEnd;

						length = end - start + 1; // Calculate new content length
						if (localDocument == 0)
						{
							long skipped = fnetis.skip(start);
						}
						else
						{
							fnetis = Channels.newInputStream(raf.getChannel().position(start));
						}

						ss = "--" + boundary + "\n\r";
						bh = ss.getBytes();
						multi_content_length += ss.length();
						dos.write(bh,0,ss.length());

						ss = "Content-Type: " + sourceMimeType + "\n\r";
						bh = ss.getBytes();
						multi_content_length += ss.length();
						dos.write(bh,0,ss.length());

						ss = "Content-Range: bytes " + start + "-" + end + "/" + size + "\n\r";
						bh = ss.getBytes();
						multi_content_length += ss.length();
						dos.write(bh,0,ss.length());

						ss = "\n\r";
						bh = ss.getBytes();
						multi_content_length += ss.length();
						dos.write(bh,0,ss.length());

               					for (int pos = 0; pos < length; pos += bytesread)
               					{
                      					bytesread = fnetis.read(b, 0, (length - pos > buffersize ? buffersize : (int)(length - pos)));
							dos.write(b,0,bytesread);
							dos.flush();
							multi_content_length += bytesread;
							//System.out.println("bytesread " + bytesread);
						}
					}
					ss = "--" + boundary + "--\n\r";
					bh = ss.getBytes();
					multi_content_length += ss.length();
					dos.write(bh,0,ss.length());
					//System.out.println("end-multi");

					response.setHeader("Content-Length", Long.toString(multi_content_length));
					response.setContentLength((int)multi_content_length);
					dos.close();
					fnetis.close();
					logger.debug("Success stream " + multi_content_length + " multipart bytes to client");
				}
				else // single byte-range
				{
					logger.debug("Success single part request received " + range);
               				if (range.startsWith("-"))
              				{
                      				// The n-number of the last bytes is requested
                       				anotherStart = size - Long.parseLong(range.substring(1));
                       			}
                       			else
                       			{
                       				arr_split = range.split("-");
                       				anotherStart = Long.parseLong(arr_split[0]);
                       				long temp = 0;
                       				anotherEnd = arr_split.length > 1 && tryParseLong(arr_split[1]) ? Long.parseLong(arr_split[1]) : size;
                       			}
                       			// Check the range and make sure it's treated according to the specs.
                       			// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
                       			// End bytes can not be larger than $end.
                       			anotherEnd = (anotherEnd > end) ? end : anotherEnd;
                       			// Validate the requested range and return an error if it's not correct.
                       			if (anotherStart > anotherEnd || anotherStart > size - 1 || anotherEnd >= size)
	               			{
						logger.debug("Failed range not satisfiable " + start + "-" + end + "/" + size);
                       				response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
						response.sendError(response.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
						fnetis.close();
						return;
                       			}
                       			start = anotherStart;
                       			end = anotherEnd;

					length = end - start + 1; // Calculate new content length
					if (localDocument == 0)
					{
						long skipped = fnetis.skip(start);
					}
					else
					{
						fnetis = Channels.newInputStream(raf.getChannel().position(start));
					}
					logger.debug("Skipped " + start + " bytes");
                        
                       			response.setStatus(response.SC_PARTIAL_CONTENT);
					response.setHeader("Content-Disposition", "inline; filename=\"" + id + "\"");
					response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
               				response.setHeader("Content-Length", Long.toString(length));
					response.setHeader("Access-Control-Allow-Origin", "*");
					response.setHeader("Accept-Ranges", "0-" + (length - 1));
					response.setContentLength((int)length);

					response.setContentType(sourceMimeType);
					logger.debug("Mime-type : " + sourceMimeType);

					dos = new DataOutputStream(response.getOutputStream());
               				byte[] b = new byte[buffersize];
					logger.debug("Buffer size : " + buffersize);
               				for (int pos = 0; pos < length; pos += bytesread)
               				{
                       				bytesread = fnetis.read(b, 0, (length - pos > buffersize ? buffersize : (int)(length - pos)));
						dos.write(b,0,bytesread);
						dos.flush();
					}
					dos.close();
					fnetis.close();
					logger.debug("Success stream " + length + " bytes to client");
				}
			}
			catch(Exception ex)
			{
				if ( fnetis != null ) fnetis.close();
				StringWriter sw = new StringWriter();
				ex.printStackTrace(new PrintWriter(sw));
				logger.debug(sw.toString());
				//response.sendError(response.SC_BAD_REQUEST);
            		}
       	    		finally
       			{
				if ( fnetis != null ) fnetis.close();
              			UserContext.get().popSubject();
				logger.debug("Success handle request");
       	    		}
		}
		catch(Exception ex)
		{
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			logger.debug(sw.toString());
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		doGet(request,response);
	}

	public void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		doGet(request,response);
	}

	private boolean tryParseLong(String value)
	{
		try  
		{
			Long.parseLong(value);  
			return true;  
		} 
		catch(NumberFormatException nfe)  
		{
			return false;  
		}
	}
}