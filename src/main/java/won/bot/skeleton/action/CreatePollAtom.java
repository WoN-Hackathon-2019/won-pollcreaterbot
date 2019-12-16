package won.bot.skeleton.action;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractCreateAtomAction;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.wonmessage.FailureResponseEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.skeleton.context.SkeletonBotContextWrapper;
import won.bot.skeleton.event.CreateAtomFromPollEvent;
import won.bot.skeleton.strawpoll.api.models.SPPoll;
import won.bot.skeleton.strawpoll.api.models.SPPollOption;
import won.protocol.message.WonMessage;
import won.protocol.service.WonNodeInformationService;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.util.RdfUtils;
import won.protocol.util.WonRdfUtils;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;

public class CreatePollAtom extends AbstractCreateAtomAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public CreatePollAtom(EventListenerContext eventListenerContext) {
        super(eventListenerContext);
    }

    protected void doRun(Event event, EventListener executingListener) throws Exception {
        EventListenerContext ctx = getEventListenerContext();
        if (event instanceof CreateAtomFromPollEvent && ctx.getBotContextWrapper() instanceof SkeletonBotContextWrapper) {
            SkeletonBotContextWrapper botContextWrapper = (SkeletonBotContextWrapper) ctx.getBotContextWrapper();
            SPPoll poll = ((CreateAtomFromPollEvent) event).getPoll();
            try {
                String title = poll.getTitle();
                long id = poll.getId();
                boolean multi = poll.isMulti();
                List<SPPollOption> options = poll.getOptions();

                WonNodeInformationService wonNodeInformationService = ctx.getWonNodeInformationService();
                final URI wonNodeUri = ctx.getNodeURISource().getNodeURI();
                final URI atomURI = wonNodeInformationService.generateAtomURI(wonNodeUri);

                DefaultAtomModelWrapper atomModelWrapper = new DefaultAtomModelWrapper(atomURI);
                atomModelWrapper.setTitle(title);
                atomModelWrapper.setDescription("Poll about " + title);

                Dataset dataset = atomModelWrapper.copyDataset();
                logger.debug("creating atom on won node {} with content {} ", wonNodeUri,
                        StringUtils.abbreviate(RdfUtils.toString(dataset), 150));
                WonMessage createAtomMessage = ctx.getWonMessageSender().prepareMessage(createWonMessage(atomURI, dataset));
                EventBotActionUtils.rememberInList(ctx, atomURI, uriListName);

                EventListener successCallback = event12 -> {
                    logger.debug("atom creation successful, new atom URI is {}", atomURI);
                };
                EventListener failureCallback = event1 -> {
                    logger.debug("atom creation failed for atom URI {}, error {}", atomURI, WonRdfUtils.MessageUtils
                            .getTextMessage(((FailureResponseEvent) event1).getFailureMessage()));
                    EventBotActionUtils.removeFromList(ctx, atomURI, uriListName);
                };
                EventBotActionUtils.makeAndSubscribeResponseListener(createAtomMessage, successCallback,
                        failureCallback, ctx);
                logger.debug("registered listeners for response to message URI {}", createAtomMessage.getMessageURI());
                //ctx.getWonMessageSender().sendWonMessage(createAtomMessage);
                logger.debug("atom creation message sent with message URI {}", createAtomMessage.getMessageURI());
            } catch (Exception e) {
                logger.error("messaging exception occurred:", e);
            }
        }
    }
}
