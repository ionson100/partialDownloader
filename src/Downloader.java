import javafx.concurrent.Task;
import org.apache.log4j.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Downloader extends Task {

    private final static String HLAM = ".nocomplete";
    private static final Logger log = Logger.getLogger ( Downloader.class );
    private HttpURLConnection con = null;
    private String urlCore, path;
    private boolean sslDiasableVerifications;

    @Override
    protected Object call () throws Exception {
        doInBackground ();
        return null;
    }

    protected void doInBackground () throws Exception {
        InputStream input = null;
        OutputStream output = null;

        try {
            if ( sslDiasableVerifications ) {
                disableSslVerification ();
            }


            int count;
            URL url = new URL ( urlCore );
            if ( urlCore.toLowerCase ().startsWith ( "https:" ) ) {
                con = (HttpsURLConnection) url.openConnection ();
            } else {
                con = (HttpURLConnection) url.openConnection ();
            }

            con.setInstanceFollowRedirects ( false );
            con.setReadTimeout ( 15000 /*milliseconds*/ );
            con.setConnectTimeout ( 20000 /* milliseconds */ );
            con.setRequestMethod ( "GET" );

            // проверка   закончена ли загрузка
            File f1 = new File ( path + HLAM );
            if ( f1.exists () ) {
                byte[] o = (byte[]) Files.getAttribute ( f1.toPath () , "user:tags" );
                String attr = new String ( o );
                con.setRequestProperty ( "Range" , String.format ( "Bytes=%s - " , f1.length () ) );
                con.setRequestProperty ( "If-Range" , attr );
            } else {
                File f2 = new File ( path );
                if ( f2.exists () ) {
                    byte[] o = (byte[]) Files.getAttribute ( f2.toPath () , "user:tags" );
                    String attr = new String ( o );
                    String res = String.format ( "W/%s" , attr );
                    con.setRequestProperty ( "If-None-Match" , res );
                }
            }


            con.connect ();
            Map <String, List <String>> map = con.getHeaderFields ();
            int code = con.getResponseCode ();// 206 add download
            if ( code == 304 ||code==404) return;
            if ( code == 200 ) {// файл измененет или  не начал качаться на клиенте
                {
                    File f = new File ( path );
                    if ( f.exists () ) ;
                    f.delete ();
                }
                {
                    File f = new File ( path + HLAM );
                    if ( f.exists () ) ;
                    f.delete ();
                }

                long httplast = con.getLastModified ();
                String etag = con.getHeaderField ( "ETag" );
                validateClientFile ( path , httplast , etag );

            }


            //"5ae31600-cb59cf"
            int lenghtOfFile = con.getContentLength () + 100;
            input = con.getInputStream ();
            output = new FileOutputStream ( path + HLAM , true );
            byte datas[] = new byte[lenghtOfFile];
            while ((count = input.read ( datas )) != -1) {
                output.write ( datas , 0 , count );
            }
            output.flush ();


        } finally {
            if ( con != null ) {
                con.disconnect ();
            }

            if ( output != null ) {
                output.close ();
            }
            if ( input != null ) {
                input.close ();
            }
            File f = new File ( path + HLAM );
            if ( f.exists () == false ) return;
            f.renameTo ( new File ( path ) );
        }
    }

    public void execute () {

        Thread thread = new Thread ( this );
        thread.start ();

    }

    public Downloader setUrl ( String urlCore ) {
        this.urlCore = urlCore.trim ();
        return this;
    }

    public Downloader setPath ( String path ) {
        this.path = path.trim ();
        return this;
    }

    public Downloader setDisableSslVerification ( boolean b ) {
        this.sslDiasableVerifications = b;
        return this;
    }

    private static void validateClientFile ( String path , long httplast , String etag ) throws IOException {


        File targetFile = new File ( path + HLAM );
        if ( targetFile.exists () )
            return;
        // создаем новый файл для закачки
        File parent = targetFile.getParentFile ();
        if ( parent != null ) {
            if ( !parent.exists () && !parent.mkdirs () ) {
                throw new IllegalStateException ( "Couldn't create dir: " + parent );
            }
        }
        // навешиваем атрибут создания файла по  времение с сервера
        targetFile.createNewFile ();
        FileTime fileTime = FileTime.fromMillis ( httplast );
        Files.setAttribute ( targetFile.toPath () , "basic:creationTime" , fileTime );
        Files.setAttribute ( targetFile.toPath () , "user:tags" , etag.getBytes () );


    }

    // отключить проверку для ssl
    private static void disableSslVerification () throws NoSuchAlgorithmException, KeyManagementException {

        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager () {
            public java.security.cert.X509Certificate[] getAcceptedIssuers () {
                return null;
            }

            public void checkClientTrusted ( X509Certificate[] certs , String authType ) {
            }

            public void checkServerTrusted ( X509Certificate[] certs , String authType ) {
            }
        }
        };
        SSLContext sc = SSLContext.getInstance ( "SSL" );
        sc.init ( null , trustAllCerts , new java.security.SecureRandom () );
        HttpsURLConnection.setDefaultSSLSocketFactory ( sc.getSocketFactory () );
        HostnameVerifier allHostsValid = new HostnameVerifier () {
            public boolean verify ( String hostname , SSLSession session ) {
                return true;
            }
        };
        HttpsURLConnection.setDefaultHostnameVerifier ( allHostsValid );

    }

    public static void main ( String[] args ) {
        String path = "E:\\";
        String url = "https://";
      //  new Downloader ().setPath ( path ).setUrl ( url ).execute ();
        ExecutorService service = Executors.newFixedThreadPool(1);
        service.submit ( new Downloader ().setPath ( path ).setUrl ( url ) );

    }


}
