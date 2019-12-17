package won.bot.skeleton.impl;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import com.sun.jndi.toolkit.url.Uri;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.behaviour.ExecuteWonMessageCommandBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandEvent;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandResultEvent;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.ConnectFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherAtomEvent;
import won.bot.framework.eventbot.filter.impl.AtomUriInNamedListFilter;
import won.bot.framework.eventbot.filter.impl.CommandResultFilter;
import won.bot.framework.eventbot.filter.impl.NotFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnFirstEventListener;
import won.bot.framework.extensions.matcher.MatcherBehaviour;
import won.bot.framework.extensions.matcher.MatcherExtension;
import won.bot.framework.extensions.serviceatom.ServiceAtomBehaviour;
import won.bot.framework.extensions.serviceatom.ServiceAtomExtension;
import won.bot.framework.extensions.textmessagecommand.TextMessageCommandBehaviour;
import won.bot.framework.extensions.textmessagecommand.TextMessageCommandExtension;
import won.bot.framework.extensions.textmessagecommand.command.EqualsTextMessageCommand;
import won.bot.framework.extensions.textmessagecommand.command.PatternMatcherTextMessageCommand;
import won.bot.framework.extensions.textmessagecommand.command.TextMessageCommand;
import won.bot.skeleton.action.CreatePollAtom;
import won.bot.skeleton.context.SkeletonBotContextWrapper;
import won.bot.skeleton.event.CreateAtomFromPollEvent;
import won.bot.skeleton.model.Poll;
import won.bot.skeleton.strawpoll.api.StrawpollAPI;
import won.protocol.model.Connection;
import won.protocol.util.WonRdfUtils;

public class PollCreatorBot extends EventBot implements MatcherExtension, ServiceAtomExtension, TextMessageCommandExtension {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private int registrationMatcherRetryInterval;
    private MatcherBehaviour matcherBehaviour;
    private ServiceAtomBehaviour serviceAtomBehaviour;
    private TextMessageCommandBehaviour textMessageCommandBehaviour;
    private HashMap<java.net.URI, Poll> polls = new HashMap<>();

    // bean setter, used by spring
    public void setRegistrationMatcherRetryInterval(final int registrationMatcherRetryInterval) {
        this.registrationMatcherRetryInterval = registrationMatcherRetryInterval;
    }

    @Override
    public ServiceAtomBehaviour getServiceAtomBehaviour() {
        return serviceAtomBehaviour;
    }

    @Override
    public MatcherBehaviour getMatcherBehaviour() {
        return matcherBehaviour;
    }

    @Override
    public TextMessageCommandBehaviour getTextMessageCommandBehaviour() {
        return textMessageCommandBehaviour;
    }

    @Override
    protected void initializeEventListeners() {
        EventListenerContext ctx = getEventListenerContext();
        if (!(getBotContextWrapper() instanceof SkeletonBotContextWrapper)) {
            logger.error(getBotContextWrapper().getBotName() + " does not work without a SkeletonBotContextWrapper");
            throw new IllegalStateException(
                            getBotContextWrapper().getBotName() + " does not work without a SkeletonBotContextWrapper");
        }
        EventBus bus = getEventBus();
        SkeletonBotContextWrapper botContextWrapper = (SkeletonBotContextWrapper) getBotContextWrapper();
        // register listeners for event.impl.command events used to tell the bot to send
        // messages
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();
        // activate ServiceAtomBehaviour
        serviceAtomBehaviour = new ServiceAtomBehaviour(ctx);
        serviceAtomBehaviour.activate();
        // set up matching extension
        // as this is an extension, it can be activated and deactivated as needed
        // if activated, a MatcherExtensionAtomCreatedEvent is sent every time a new
        // atom is created on a monitored node
        matcherBehaviour = new MatcherBehaviour(ctx, "BotSkeletonMatchingExtension", registrationMatcherRetryInterval);
        //matcherBehaviour.activate();
        // create filters to determine which atoms the bot should react to
        NotFilter noOwnAtoms = new NotFilter(
                        new AtomUriInNamedListFilter(ctx, ctx.getBotContextWrapper().getAtomCreateListName()));
        // filter to prevent reacting to serviceAtom<->ownedAtom events;
        NotFilter noInternalServiceAtomEventFilter = getNoInternalServiceAtomEventFilter();
        bus.subscribe(ConnectFromOtherAtomEvent.class, noInternalServiceAtomEventFilter, new BaseEventBotAction(ctx) {
            @Override
            protected void doRun(Event event, EventListener executingListener) {
                EventListenerContext ctx = getEventListenerContext();
                ConnectFromOtherAtomEvent connectFromOtherAtomEvent = (ConnectFromOtherAtomEvent) event;
                try {
                    String message = "Hello, I\'m the PollCreatorBot! \nEnter `new poll` to create a new poll";
                    final ConnectCommandEvent connectCommandEvent = new ConnectCommandEvent(
                                    connectFromOtherAtomEvent.getRecipientSocket(),
                                    connectFromOtherAtomEvent.getSenderSocket(), message);
                    ctx.getEventBus().subscribe(ConnectCommandSuccessEvent.class, new ActionOnFirstEventListener(ctx,
                                    new CommandResultFilter(connectCommandEvent), new BaseEventBotAction(ctx) {
                                        @Override
                                        protected void doRun(Event event, EventListener executingListener) {
                                            ConnectCommandResultEvent connectionMessageCommandResultEvent = (ConnectCommandResultEvent) event;
                                            if (!connectionMessageCommandResultEvent.isSuccess()) {
                                                logger.error("Failure when trying to open a received Request: "
                                                                + connectionMessageCommandResultEvent.getMessage());
                                            } else {
                                                logger.info(
                                                                "Add an established connection " +
                                                                                connectCommandEvent.getLocalSocket()
                                                                                + " -> "
                                                                                + connectCommandEvent.getTargetSocket()
                                                                                +
                                                                                " to the botcontext ");
                                                botContextWrapper.addConnectedSocket(
                                                                connectCommandEvent.getLocalSocket(),
                                                                connectCommandEvent.getTargetSocket());
                                            }
                                        }
                                    }));
                    ctx.getEventBus().publish(connectCommandEvent);
                } catch (Exception te) {
                    logger.error(te.getMessage(), te);
                }
            }
        });


        ArrayList<TextMessageCommand> botCommands = new ArrayList<>();
        botCommands.add(new EqualsTextMessageCommand("new poll", "New poll will be created", "new poll",
                (Connection connection) -> {
                    Poll poll = polls.get(connection.getTargetSocketURI());
                    if (poll == null) polls.put(connection.getTargetSocketURI(), new Poll());
                    poll = polls.get(connection.getTargetSocketURI());
                    if(poll.getTitle()!=null || poll.isTypingExpiration() || poll.isTypingPollContent() || poll.isAddingTags()){
                        poll.setTypingExpiration(false);
                        poll.setTypingPollContent(false);
                        poll.setAddingTags(false);
                        bus.publish(new ConnectionMessageCommandEvent(connection, "Previous poll was canceled!"));
                        polls.put(connection.getTargetSocketURI(), new Poll());
                    }
                    poll = polls.get(connection.getTargetSocketURI());
                    bus.publish(new ConnectionMessageCommandEvent(connection, "Please enter the expiration date of the poll (format: dd/MM/yyyy)"));
                    poll.setTypingExpiration(true);
                }));
        botCommands.add(new EqualsTextMessageCommand("end", "Add some Tags", "end",
                (Connection connection) -> {
                    Poll poll = polls.get(connection.getTargetSocketURI());
                    if(poll == null) {
                        bus.publish(new ConnectionMessageCommandEvent(connection, "You have to create a Poll!!"));
                        return;
                    }
                    if(poll.getTitle() == null) bus.publish(new ConnectionMessageCommandEvent(connection, "You have to create a new poll before you can publish"));
                    else if(poll.getAnswers().size() < 2) bus.publish(new ConnectionMessageCommandEvent(connection, "Your poll has to have at least two answers"));
                    else {
                        bus.publish(new ConnectionMessageCommandEvent(connection, "Before we create the poll, you can add some tags to find your poll.\n Just enter the tags as you did with the answers\ntype `done`, if you want us to publish the poll"));
                        poll.setTypingPollContent(false);
                        poll.setAddingTags(true);
                    }
                }));

        botCommands.add(new EqualsTextMessageCommand("done", "Poll has been created.", "done",
                (Connection connection) -> {
                    Poll poll = polls.get(connection.getTargetSocketURI());
                    if(poll == null) {
                        bus.publish(new ConnectionMessageCommandEvent(connection, "You have to create a Poll!!"));
                        return;
                    }
                    if(poll.getTitle() == null) bus.publish(new ConnectionMessageCommandEvent(connection, "You have to create a new poll before you can publish"));
                    else if(poll.getAnswers().size() < 2) bus.publish(new ConnectionMessageCommandEvent(connection, "Your poll has to have at least two answers"));
                    else {
                        poll.setTypingPollContent(false);
                        poll.setAddingTags(false);
                        try {
                            poll.setId(StrawpollAPI.create(poll.getTitle(), poll.getAnswers()));
                            logger.info("Created Poll ID:" + poll.getId());
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                            bus.publish(new ConnectionMessageCommandEvent(connection, "An error occurred while creating the poll...\nPlease try again later"));
                        }
                        bus.publish(new ConnectionMessageCommandEvent(connection, poll.toString()));
                        bus.publish(new ConnectionMessageCommandEvent(connection, "Poll ID: " + poll.getId()));
                        bus.publish(new ConnectionMessageCommandEvent(connection, "If you wanna create a new poll just enter `new poll`"));
                        bus.publish(new CreateAtomFromPollEvent(poll));
                        polls.put(connection.getTargetSocketURI(), new Poll());
                    }
                }));
        // activate TextMessageCommandBehaviour
        textMessageCommandBehaviour = new TextMessageCommandBehaviour(ctx,
                botCommands.toArray(new TextMessageCommand[0]));
        textMessageCommandBehaviour.activate();

        bus.subscribe(CreateAtomFromPollEvent.class, new CreatePollAtom(ctx));

        bus.subscribe(MessageFromOtherAtomEvent.class, noInternalServiceAtomEventFilter, new BaseEventBotAction(ctx) {
            @Override
            protected void doRun(Event event, EventListener eventListener) throws Exception {

                MessageFromOtherAtomEvent msgEvent = (MessageFromOtherAtomEvent) event;
                String text = WonRdfUtils.MessageUtils.getTextMessage(msgEvent.getWonMessage());
                Poll poll = polls.get(msgEvent.getCon().getTargetSocketURI());
                if(poll == null) {
                    //bus.publish(new ConnectionMessageCommandEvent(msgEvent.getCon(), "You have to create a Poll!!"));
                    return;
                }
                if (poll.isTypingExpiration() && !text.equals("end") && !text.equals("done") && !text.equals("new poll")) {
                    DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy");
                    try{
                        poll.setExpiriation(DateTime.parse(text, formatter));
                    }catch(Exception e){
                        logger.error("Wrong format: " + e.getMessage());
                        bus.publish(new ConnectionMessageCommandEvent(msgEvent.getCon(), "Please type in a valid Date"));
                        return;
                    }

                    poll.setTypingExpiration(false);
                    bus.publish(new ConnectionMessageCommandEvent(msgEvent.getCon(), "Please enter your question"));
                    poll.setTypingPollContent(true);
                }else if (poll.getTitle() == null && poll.isTypingPollContent() && !text.equals("new poll")){
                    poll.setTitle(text);
                    bus.publish(new ConnectionMessageCommandEvent(msgEvent.getCon(), "Please enter the answers (every answer must be one message)\nTo publish the poll enter `end`"));
                } else if(poll.getTitle() != null && poll.isTypingPollContent() && !text.equals("end")){
                        if(!poll.getAnswers().contains(text)) poll.addAnswer(text);
                } else if (poll.getTitle() != null && poll.isAddingTags() && !text.equals("end") && !text.equals("done")) {
                        if(!poll.getTags().contains(text))poll.addTags(text);
                }
                //bus.publish(new ConnectionMessageCommandEvent(msgEvent.getCon(), text));
            }
        });
    }
}
