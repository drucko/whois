package net.ripe.db.whois.api.rest;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.ripe.db.whois.api.rest.domain.ErrorMessage;
import net.ripe.db.whois.api.rest.domain.Link;
import net.ripe.db.whois.api.rest.domain.WhoisResources;
import net.ripe.db.whois.api.rest.mapper.WhoisObjectServerMapper;
import net.ripe.db.whois.common.DateTimeProvider;
import net.ripe.db.whois.common.Message;
import net.ripe.db.whois.common.Messages;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.update.domain.Credential;
import net.ripe.db.whois.update.domain.Credentials;
import net.ripe.db.whois.update.domain.Keyword;
import net.ripe.db.whois.update.domain.Operation;
import net.ripe.db.whois.update.domain.Origin;
import net.ripe.db.whois.update.domain.OverrideCredential;
import net.ripe.db.whois.update.domain.Paragraph;
import net.ripe.db.whois.update.domain.PasswordCredential;
import net.ripe.db.whois.update.domain.Update;
import net.ripe.db.whois.update.domain.UpdateContext;
import net.ripe.db.whois.update.domain.UpdateMessages;
import net.ripe.db.whois.update.domain.UpdateRequest;
import net.ripe.db.whois.update.domain.UpdateStatus;
import net.ripe.db.whois.update.handler.UpdateRequestHandler;
import net.ripe.db.whois.update.log.LoggerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InternalUpdatePerformer {
    private final UpdateRequestHandler updateRequestHandler;
    private final DateTimeProvider dateTimeProvider;
    private final WhoisObjectServerMapper whoisObjectMapper;
    private final LoggerContext loggerContext;

    @Autowired
    public InternalUpdatePerformer(final UpdateRequestHandler updateRequestHandler,
                                   final DateTimeProvider dateTimeProvider,
                                   final WhoisObjectServerMapper whoisObjectMapper,
                                   final LoggerContext loggerContext) {
        this.updateRequestHandler = updateRequestHandler;
        this.dateTimeProvider = dateTimeProvider;
        this.whoisObjectMapper = whoisObjectMapper;
        this.loggerContext = loggerContext;
    }

    public Response performUpdate(final Origin origin, final Update update, final String content, final Keyword keyword, final HttpServletRequest request) {
        loggerContext.init(getRequestId(origin.getFrom()));
        try {
            final UpdateContext updateContext = new UpdateContext(loggerContext);
            final boolean notificationsEnabled = true;

            logHttpHeaders(loggerContext, request);

            final UpdateRequest updateRequest = new UpdateRequest(
                    origin,
                    keyword,
                    content,
                    Lists.newArrayList(update),
                    notificationsEnabled);

            updateRequestHandler.handle(updateRequest, updateContext);

            final RpslObject responseObject = updateContext.getPreparedUpdate(update).getUpdatedObject();

            Response.ResponseBuilder responseBuilder;
            UpdateStatus status = updateContext.getStatus(update);
            if (status == UpdateStatus.FAILED_AUTHENTICATION) {
                responseBuilder = Response.status(Response.Status.UNAUTHORIZED);
            } else if (status == UpdateStatus.EXCEPTION) {
                responseBuilder = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
            } else if (status != UpdateStatus.SUCCESS) {
                if (updateContext.getMessages(update).contains(UpdateMessages.newKeywordAndObjectExists())){
                    responseBuilder = Response.status(Response.Status.CONFLICT);
                } else {
                    responseBuilder = Response.status(Response.Status.BAD_REQUEST);
                }
            } else {
                responseBuilder = Response.status(Response.Status.OK);
            }

            responseBuilder.entity(createResponse(request, updateContext, update, responseObject));
            return responseBuilder.build();
        } finally {
            loggerContext.remove();
        }
    }

    private WhoisResources createResponse(final HttpServletRequest request, UpdateContext updateContext, Update update, RpslObject responseObject) {
        final WhoisResources whoisResources = new WhoisResources();
        // global messages
        List<ErrorMessage> errorMessages = Lists.newArrayList();
        for (Message message : updateContext.getGlobalMessages().getAllMessages()) {
            errorMessages.add(new ErrorMessage(message));
        }

        // object messages
        for (Message message : updateContext.getMessages(update).getMessages().getAllMessages()) {
            errorMessages.add(new ErrorMessage(message));
        }

        // attribute messages
        for (Map.Entry<RpslAttribute, Messages> entry: updateContext.getMessages(update).getAttributeMessages().entrySet()) {
            RpslAttribute rpslAttribute = entry.getKey();
            for (Message message : entry.getValue().getAllMessages()) {
                errorMessages.add(new ErrorMessage(message, rpslAttribute));
            }
        }

        if (!errorMessages.isEmpty()) {
            whoisResources.setErrorMessages(errorMessages);
        }
        whoisResources.setWhoisObjects(Collections.singletonList(whoisObjectMapper.map(responseObject)));
        whoisResources.setLink(new Link("locator", RestServiceHelper.getRequestURL(request).replaceFirst("/whois", "")));
        whoisResources.includeTermsAndConditions();
        return whoisResources;
    }


    public Update createUpdate(final RpslObject rpslObject, final List<String> passwords, final String deleteReason, String override) {
        return new Update(
                createParagraph(rpslObject, passwords, override),
                deleteReason != null ? Operation.DELETE : Operation.UNSPECIFIED,
                deleteReason != null ? Lists.newArrayList(deleteReason) : null,
                rpslObject);
    }

    private Paragraph createParagraph(final RpslObject rpslObject, final List<String> passwords, String override) {
        final Set<Credential> credentials = Sets.newHashSet();
        for (String password : passwords) {
            credentials.add(new PasswordCredential(password));
        }
        if (override != null) {
            credentials.add(OverrideCredential.parse(override));
        }

        return new Paragraph(rpslObject.toString(), new Credentials(credentials));
    }

    public Origin createOrigin(final HttpServletRequest request) {
        return new WhoisRestApi(dateTimeProvider, request.getRemoteAddr());
    }

    public String createContent(final RpslObject rpslObject, final List<String> passwords, final String deleteReason, String override) {
        final StringBuilder builder = new StringBuilder();
        builder.append(rpslObject.toString());

        if (builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }

        if (deleteReason != null) {
            builder.append("delete: ");
            builder.append(deleteReason);
            builder.append("\n\n");
        }

        if (passwords != null) {
            for (final String password : passwords) {
                builder.append("password: ");
                builder.append(password);
                builder.append('\n');
            }
        }

        if (override != null) {
            builder.append("override: ");
            builder.append(override);
            builder.append("\n\n");
        }

        return builder.toString();
    }

    private String getRequestId(final String remoteAddress) {
        return String.format("rest_%s_%s", remoteAddress, dateTimeProvider.getNanoTime());
    }

    public static void logHttpHeaders(final LoggerContext loggerContext, final HttpServletRequest request) {
        final Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            final String name = names.nextElement();
            final Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                loggerContext.log(new Message(Messages.Type.INFO, String.format("Header: %s=%s", name, values.nextElement())));
            }
        }
    }
}
