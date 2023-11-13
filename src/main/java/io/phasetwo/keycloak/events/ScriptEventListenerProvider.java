package io.phasetwo.keycloak.events;

import io.phasetwo.keycloak.config.Configurable;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ScriptModel;
import org.keycloak.scripting.InvocableScriptAdapter;
import org.keycloak.scripting.ScriptBindingsConfigurer;
import org.keycloak.scripting.ScriptExecutionException;
import org.keycloak.scripting.ScriptingProvider;

/** An event listener that runs a user-provided script */
@JBossLog
public class ScriptEventListenerProvider implements EventListenerProvider, Configurable {

  protected static final String SCRIPTS_DISABLED_ENV = "SCRIPTS_DISABLED";
  protected static final String ON_EVENT_FUNCTION_NAME = "onEvent";
  protected static final String ON_ADMIN_EVENT_FUNCTION_NAME = "onAdminEvent";
  protected static final String SCRIPT_CODE = "scriptCode";
  protected static final String SCRIPT_NAME = "scriptName";
  protected static final String SCRIPT_DESCRIPTION = "scriptDescription";

  protected final KeycloakSession session;
  protected final boolean scriptsDisabled;

  public ScriptEventListenerProvider(KeycloakSession session) {
    this.session = session;
    this.scriptsDisabled = Boolean.parseBoolean(System.getenv(SCRIPTS_DISABLED_ENV));
  }

  protected Map<String, Object> config;

  @Override
  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  @Override
  public void onEvent(Event event) {
    if (scriptsDisabled) return;
    log.debugf("run event in js\n%s", config.get(SCRIPT_CODE).toString());
    InvocableScriptAdapter invocableScriptAdapter =
        getInvocableScriptAdapter(
            event.getRealmId(),
            bindings -> {
              bindings.put("event", event);
              bindings.put("realm", Events.getRealm(session, event));
              bindings.put("user", Events.getUser(session, event));
              bindings.put("session", session);
              bindings.put("LOG", log);
            });
    tryInvoke(invocableScriptAdapter, ON_EVENT_FUNCTION_NAME, event);
  }

  @Override
  public void onEvent(AdminEvent event, boolean b) {
    if (scriptsDisabled) return;
    log.debugf("run admin event in js\n%s", config.get(SCRIPT_CODE).toString());
    InvocableScriptAdapter invocableScriptAdapter =
        getInvocableScriptAdapter(
            event.getRealmId(),
            bindings -> {
              bindings.put("event", event);
              bindings.put("realm", Events.getRealm(session, event));
              bindings.put("session", session);
              bindings.put("LOG", log);
            });
    tryInvoke(invocableScriptAdapter, ON_ADMIN_EVENT_FUNCTION_NAME, event, b);
  }

  @Override
  public void close() {
    // close this instance of the event listener
  }

  private void tryInvoke(
      InvocableScriptAdapter invocableScriptAdapter, String functionName, Object... args) {
    if (!invocableScriptAdapter.isDefined(functionName)) {
      log.warnf("%s not defined in %s", functionName, config.get(SCRIPT_NAME).toString());
      return;
    }
    try {
      log.debugf("Invoking script function %s", functionName);
      invocableScriptAdapter.invokeFunction(functionName, args);
    } catch (ScriptExecutionException e) {
      log.error("Error in script execution", e);
    }
  }

  private InvocableScriptAdapter getInvocableScriptAdapter(
      String realmId, ScriptBindingsConfigurer bindings) {
    String scriptName = config.get(SCRIPT_NAME).toString();
    String scriptCode = config.get(SCRIPT_CODE).toString();
    String scriptDescription = config.get(SCRIPT_DESCRIPTION).toString();

    ScriptingProvider scripting = session.getProvider(ScriptingProvider.class);
    ScriptModel script =
        scripting.createScript(
            realmId, ScriptModel.TEXT_JAVASCRIPT, scriptName, scriptCode, scriptDescription);

    return scripting.prepareInvocableScript(script, bindings);
  }
}
