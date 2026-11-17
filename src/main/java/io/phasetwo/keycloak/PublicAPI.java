package io.phasetwo.keycloak;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or method as part of this extension's public API: something other extensions may
 * implement or call, and that follows the project's compatibility and deprecation policy.
 *
 * <p>APIs marked {@code unstable = true} may still change incompatibly between releases.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface PublicAPI {
  /**
   * Whether this API may still change incompatibly.
   *
   * @return {@code true} if the API is unstable
   */
  boolean unstable() default false;
}
