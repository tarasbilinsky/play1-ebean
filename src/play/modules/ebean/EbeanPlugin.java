package play.modules.ebean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.sql.DataSource;
import javax.xml.crypto.Data;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Binder;
import play.db.DB;
import play.db.Model;
import play.db.Model.Property;
import play.db.jpa.JPAPlugin;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.mvc.Http.Request;

import io.ebean.Database;
import io.ebean.DatabaseFactory;
import io.ebean.Query;
import io.ebean.Update;
import io.ebean.config.DatabaseConfig;

public class EbeanPlugin extends PlayPlugin
{
  private static Database              defaultServer;
  private static Map<String, Database> SERVERS = new HashMap<String,Database>();

  private static String allStackTrace(Throwable t) {
    return Arrays.stream(t.getStackTrace()).map(x -> x.toString()).reduce((a, b) -> a + "\n" + b).get().toString();
  }
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static Database createServer(String name, DataSource dataSource)
  {
    Database result = null;
    DatabaseConfig cfg = new DatabaseConfig();
    cfg.loadFromProperties();
    cfg.setName(name);
    cfg.setClasses((List) Play.classloader.getAllClasses());
    cfg.setDataSource(new EbeanDataSourceWrapper(dataSource));
    cfg.setRegister("default".equals(name));
    cfg.setDefaultServer("default".equals(name));
    try {
      result = DatabaseFactory.create(cfg);
    } catch (Throwable t) {
      StringBuilder b = new StringBuilder();
      Throwable tt = t;
      while (tt != null) {
        b.append("\n\n\n");
        b.append(allStackTrace(tt));
        tt = tt.getCause();
      }

      Logger.error("Failed to create ebean server (%s) %s ", t.getMessage(), b.toString());
    }
    return result;
  }

  protected static Database checkServer(String name, DataSource ds)
  {
    Database server = null;
    if (name != null) {
      synchronized (SERVERS) {
        server = SERVERS.get(name);
        if (server == null) {
          server = createServer(name, ds);
          SERVERS.put(name, server);
        }
      }
    }
    return server;
  }
  
  public EbeanPlugin()
  {
    super();
  }

  @Override
  public void onConfigurationRead()
  {
    PlayPlugin jpaPlugin = Play.pluginCollection.getPluginInstance(JPAPlugin.class);
    if (jpaPlugin != null && Play.pluginCollection.isEnabled(jpaPlugin)) {
      Logger.debug("EBEAN: Disabling JPAPlugin in order to replace JPA implementation");
      Play.pluginCollection.disablePlugin(jpaPlugin);
    }
  }

  @Override
  public void onApplicationStart()
  {
    if (DB.datasource != null) {
      Logger.debug("EBEAN: Creating default server");
      defaultServer = createServer("default", DB.datasource);
    }
  }

  @Override
  public void onApplicationStop()
  {
    Logger.debug("EBEAN: close all servers");
    SERVERS.clear();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void beforeInvocation()
  {
    Database server = defaultServer;

    Request currentRequest = Http.Request.current();
    if (currentRequest != null) {
      // Hook to introduce more data sources
      Map<String, DataSource> ds = (Map<String, DataSource>) currentRequest.args.get("dataSources");
      if (ds != null && ds.size() > 0) {
        // Currently we support single data source
        Map.Entry<String,DataSource> firstEntry = ds.entrySet().iterator().next();
        server = checkServer(firstEntry.getKey(),firstEntry.getValue());
      }
    }
    EbeanContext.set(server);
  }

  @Override
  public void afterInvocation()
  {
    Database ebean = EbeanContext.server();
    if (ebean != null && ebean.currentTransaction() != null) ebean.currentTransaction().commit();
  }

  @Override
  public void invocationFinally()
  {
    Database ebean = null;
    try {
      ebean = EbeanContext.server();
    } catch(IllegalStateException e) {
      Logger.error(e, "EbeanPlugin ending transaction in finally");
    }
    if (ebean != null) ebean.endTransaction();
    EbeanContext.set(null);
  }

  @Override
  public void enhance(ApplicationClass applicationClass) throws Exception
  {
    try {
      EbeanEnhancer.class.newInstance().enhanceThisClass(applicationClass);
    } catch (Throwable t) {
      Logger.error(t, "EbeanPlugin enhancement error");
    }
  }

}
