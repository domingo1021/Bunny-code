/**
 * Cross-cutting building blocks every module may use: the error model, HTTP/STOMP plumbing,
 * security wiring, rate limiting and time. Contains no business rules.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.bunnycode.shared;

import org.springframework.modulith.ApplicationModule;
