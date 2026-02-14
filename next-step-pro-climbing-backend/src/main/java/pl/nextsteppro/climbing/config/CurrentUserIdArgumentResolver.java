package pl.nextsteppro.climbing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticatedUser;

import java.util.UUID;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
            && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // Try JWT first
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthenticatedUser jwtUser) {
            return jwtUser.getUserId();
        }

        // Try OAuth2
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserId();
        }

        // Fallback to dev session
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object userId = session.getAttribute("DEV_USER_ID");
                if (userId instanceof UUID uuid) {
                    return uuid;
                }
            }
        }

        return null;
    }
}
