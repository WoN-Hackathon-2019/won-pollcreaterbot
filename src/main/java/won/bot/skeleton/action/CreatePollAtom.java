package won.bot.skeleton.action;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractCreateAtomAction;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.FailureResponseEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.skeleton.context.SkeletonBotContextWrapper;
import won.bot.skeleton.event.CreateAtomFromPollEvent;
import won.bot.skeleton.model.Poll;
import won.bot.skeleton.model.SCHEMA_EXTENDED;
import won.bot.skeleton.strawpoll.api.models.SPPoll;
import won.bot.skeleton.strawpoll.api.models.SPPollOption;
import won.protocol.message.WonMessage;
import won.protocol.service.WonNodeInformationService;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.util.RdfUtils;
import won.protocol.util.WonRdfUtils;
import org.apache.jena.rdf.model.Resource;
import won.protocol.vocabulary.SCHEMA;
import won.protocol.vocabulary.WON;
import won.protocol.vocabulary.WONCON;
import won.protocol.vocabulary.WONMATCH;

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
            Poll poll = ((CreateAtomFromPollEvent) event).getPoll();
            try {
                String title = poll.getTitle();
                long id = poll.getId();

                //Creates new atom
                // Create a new atom URI
                final URI wonNodeUri = ctx.getNodeURISource().getNodeURI();
                final URI atomURI = ctx.getWonNodeInformationService().generateAtomURI(wonNodeUri);

                // Set atom data
                DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
                atomWrapper.setTitle("Poll: \"" + title + "\"");
                atomWrapper.setDescription("This is a poll atom, use the PollVoteBot to vote on this poll.\n\nInfo:\nTopic:\t " + title + "\nID:\t " + Long.toString(id));
                for (String s : poll.getTags()) {
                    atomWrapper.addTag(s);
                }

                Resource atom = atomWrapper.getAtomModel().createResource(atomURI.toString());
                atom.addProperty(RDF.type, ModelFactory.createDefaultModel().createProperty(WON.BASE_URI, "PollAtom"));
                atom.addProperty(SCHEMA_EXTENDED.ID, Long.toString(id));
                atom.addProperty(SCHEMA.NAME, title);
                atom.addProperty(WONMATCH.doNotMatchAfter, poll.getExpiriation().toString());

                //publish command
                CreateAtomCommandEvent createCommand = new CreateAtomCommandEvent(atomWrapper.getDataset(), "atom_uris");
                ctx.getEventBus().publish(createCommand);
            } catch (Exception e) {
                logger.error("messaging exception occurred:", e);
            }
        }
    }
}
