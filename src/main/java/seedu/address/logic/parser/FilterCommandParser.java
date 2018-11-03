package seedu.address.logic.parser;

import static seedu.address.commons.core.Messages.MESSAGE_INVALID_COMMAND_FORMAT;
import static seedu.address.ui.ResultDisplay.TEXT_STYLE_CLASS_DEFAULT;
import static seedu.address.ui.ResultDisplay.TEXT_STYLE_CLASS_ERROR;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import seedu.address.logic.commands.FilterCommand;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.logic.parser.exceptions.RichParseException;
import seedu.address.logic.parser.tokenizer.BooleanExpressionParser;
import seedu.address.logic.parser.tokenizer.StringTokenizer;
import seedu.address.logic.parser.tokenizer.exceptions.BooleanExpressionInvalidOperatorException;
import seedu.address.logic.parser.tokenizer.exceptions.BooleanExpressionMismatchedLeftBracketException;
import seedu.address.logic.parser.tokenizer.exceptions.BooleanExpressionMismatchedRightBracketException;
import seedu.address.logic.parser.tokenizer.exceptions.BooleanExpressionUnexpectedBinaryOperatorException;
import seedu.address.logic.parser.tokenizer.exceptions.BooleanExpressionUnexpectedEndOfStringException;
import seedu.address.logic.parser.tokenizer.exceptions.BooleanExpressionUnexpectedRightBracketException;
import seedu.address.logic.parser.tokenizer.exceptions.TokenizationEndOfStringException;
import seedu.address.logic.parser.tokenizer.exceptions.TokenizationException;
import seedu.address.logic.parser.tokenizer.exceptions.TokenizationMismatchException;
import seedu.address.logic.parser.tokenizer.exceptions.TokenizationMissingEndQuoteException;
import seedu.address.logic.parser.tokenizer.exceptions.TokenizationNoMatchableCharacterException;
import seedu.address.logic.parser.tokenizer.exceptions.TokenizationUnexpectedQuoteException;
import seedu.address.model.attachment.Attachment;
import seedu.address.model.tag.Tag;
import seedu.address.model.task.Deadline;
import seedu.address.model.task.FilterOperator;
import seedu.address.model.task.Frequency;
import seedu.address.model.task.Name;
import seedu.address.model.task.Priority;
import seedu.address.model.task.Task;
import seedu.address.model.task.exceptions.InvalidPredicateException;
import seedu.address.model.task.exceptions.InvalidPredicateOperatorException;
import seedu.address.model.task.exceptions.InvalidPredicateTestPhraseException;
import seedu.address.model.util.SetUtil;
import seedu.address.ui.ResultDisplay;

/**
 * Parses input arguments and creates a new FilterCommand object
 */
public class FilterCommandParser implements Parser<FilterCommand> {

    private static final String MESSAGE_INVALID_KEY_FORMAT = "Invalid key: %1$s";
    private static final String MESSAGE_INVALID_KEY_DOUBLE_OPERATOR_FORMAT =
        "Invalid key for set-based filter: %1$s";
    private static final String MESSAGE_INVALID_OPERATOR_FORMAT = "Invalid filter operator: %1$s";
    private static final String MESSAGE_INVALID_TESTPHRASE_FORMAT = "Invalid filter test value: %1$s";

    private static final String KEY_NAME_SHORT = "n";
    private static final String KEY_NAME_LONG = "name";
    private static final String KEY_DEADLINE_SHORT = "d";
    private static final String KEY_DEADLINE_LONG = "due";
    private static final String KEY_PRIORITY_SHORT = "p";
    private static final String KEY_PRIORITY_LONG = "priority";
    private static final String KEY_FREQUENCY_SHORT = "f";
    private static final String KEY_FREQUENCY_LONG = "frequency";
    private static final String KEY_TAG_SHORT = "t";
    private static final String KEY_TAG_LONG = "tag";
    private static final String KEY_ATTACHMENT_SHORT = "a";
    private static final String KEY_ATTACHMENT_LONG = "attachment";

    private static final Predicate<Task> ALWAYS_FALSE = task -> false;


    // used to match things like:
    // due=1/10/2018
    // test<blah
    // name>"hello world"
    // note: '/' is necessary for dates, ',' is necessary for tags
    private static final Predicate<Character> ALLOWED_UNQUOTED_CHARACTER_PREDICATE =
        ch -> (ch >= 'A' && ch <= 'Z')
            || (ch >= 'a' && ch <= 'z')
            || (ch >= '0' && ch <= '9')
            || ch == '_'
            || ch == '-'
            || ch == '/'
            || ch == '\\'
            || ch == ','
            || ch == '.';

    private static final Predicate<Character> ALLOWED_KEY_CHARACTER_PREDICATE =
        ch -> (ch >= 'A' && ch <= 'Z')
            || (ch >= 'a' && ch <= 'z');

    private static final Pattern FILTER_OPERATOR_PATTERN = Pattern.compile("[\\=\\<\\>\\:]");

    /**
     * Creates a predicate that filters by name.
     */
    private static Predicate<Task> createNamePredicate(FilterOperator operator, String testPhrase)
        throws InvalidPredicateOperatorException {
        Predicate<Name> namePredicate = Name.makeFilter(operator, testPhrase);
        return task -> namePredicate.test(task.getName());
    }

    /**
     * Creates a predicate that filters by deadline.
     */
    private static Predicate<Task> createDeadlinePredicate(FilterOperator operator, String testPhrase)
        throws InvalidPredicateTestPhraseException, InvalidPredicateOperatorException {
        Predicate<Deadline> deadlinePredicate = Deadline.makeFilter(operator, testPhrase);
        return task -> deadlinePredicate.test(task.getDeadline());
    }

    /**
     * Creates a predicate that filters by priority.
     */
    private static Predicate<Task> createPriorityPredicate(FilterOperator operator, String testPhrase)
        throws InvalidPredicateTestPhraseException, InvalidPredicateOperatorException {
        Predicate<Priority> priorityPredicate = Priority.makeFilter(operator, testPhrase);
        return task -> priorityPredicate.test(task.getPriority());
    }

    /**
     * Creates a predicate that filters by frequency.
     */
    private static Predicate<Task> createFrequencyPredicate(FilterOperator operator, String testPhrase)
        throws InvalidPredicateTestPhraseException, InvalidPredicateOperatorException {
        Predicate<Frequency> frequencyPredicate = Frequency.makeFilter(operator, testPhrase);
        return task -> frequencyPredicate.test(task.getFrequency());
    }

    /**
     * Creates a predicate that filters by tags.
     */
    private static Predicate<Task> createTagsPredicate(FilterOperator setOperator, FilterOperator fieldOperator,
                                                       String testPhrase)
        throws InvalidPredicateTestPhraseException, InvalidPredicateOperatorException {
        Predicate<Set<Tag>> tagsPredicate = SetUtil.makeFilter(Tag.class, setOperator, fieldOperator, testPhrase);
        return task -> tagsPredicate.test(task.getTags());
    }

    /**
     * Creates a predicate that filters by tags.
     */
    private static Predicate<Task> createAttachmentsPredicate(FilterOperator setOperator, FilterOperator fieldOperator,
                                                              String testPhrase)
        throws InvalidPredicateTestPhraseException, InvalidPredicateOperatorException {
        Predicate<Set<Attachment>> attachmentsPredicate = SetUtil.makeFilter(Attachment.class,
            setOperator, fieldOperator, testPhrase);
        return task -> attachmentsPredicate.test(task.getAttachments());
    }

    /**
     * A functional interface that represents a supplier than can throw InvalidPredicateException.
     */
    @FunctionalInterface
    private interface ExceptionalSupplier<T> {
        T get() throws InvalidPredicateException;
    }

    /**
     * Invokes the supplier, and converts an InvalidPredicateException to a predicate that always returns false.
     *
     * @param supplier The supplier that either produces a predicate or throws an exception.
     * @return On success returns the predicate that is returned by the supplier,
     * on failure returns a predicate that is always false.
     */
    private static Predicate<Task> silencePredicateException(ExceptionalSupplier<Predicate<Task>> supplier) {
        try {
            return supplier.get();
        } catch (InvalidPredicateException e) {
            return ALWAYS_FALSE;
        }
    }

    /**
     * Creates a predicate from the specified key, operator, and test phrase.
     *
     * @param key        The key that refers to the specific field in a task.
     * @param operator   The filter operator.
     * @param testPhrase The test phrase to compare with the specific field of each task.
     * @return The predicate that is created.
     */
    private static Predicate<Task> createPredicate(String key, FilterOperator operator, String testPhrase)
        throws ParseException {

        try {
            switch (key) {
            case KEY_NAME_SHORT: // fallthrough
            case KEY_NAME_LONG:
                return createNamePredicate(operator, testPhrase);
            case KEY_DEADLINE_SHORT: // fallthrough
            case KEY_DEADLINE_LONG:
                return createDeadlinePredicate(operator, testPhrase);
            case KEY_PRIORITY_SHORT: // fallthrough
            case KEY_PRIORITY_LONG:
                return createPriorityPredicate(operator, testPhrase);
            case KEY_FREQUENCY_SHORT: // fallthrough
            case KEY_FREQUENCY_LONG:
                return createFrequencyPredicate(operator, testPhrase);
            case KEY_TAG_SHORT: // fallthrough
            case KEY_TAG_LONG:
                return createTagsPredicate(FilterOperator.CONVENIENCE, operator, testPhrase);
            case KEY_ATTACHMENT_SHORT: // fallthrough
            case KEY_ATTACHMENT_LONG:
                return createAttachmentsPredicate(FilterOperator.CONVENIENCE, operator, testPhrase);
            default:
                throw new ParseException(String.format(MESSAGE_INVALID_KEY_FORMAT, key));
            }
        } catch (InvalidPredicateOperatorException e) {
            throw new ParseException(String.format(MESSAGE_INVALID_OPERATOR_FORMAT, operator), e);
        } catch (InvalidPredicateTestPhraseException e) {
            throw new ParseException(String.format(MESSAGE_INVALID_TESTPHRASE_FORMAT, testPhrase), e);
        }
    }

    /**
     * Creates a predicate from the specified key, two operators, and test phrase.
     * This overload is used for filters of set-based fields (e.g. tags and attachments.
     * The set operator and the field-specific operator can be specified independently.
     *
     * @param key           The key that refers to the specific field in a task.
     * @param setOperator   The set filter operator.
     * @param fieldOperator The field-specific filter operator.
     * @param testPhrase    The test phrase to compare with the specific field of each task.
     * @return The predicate that is created.
     */
    private static Predicate<Task> createPredicate(String key,
                                                   FilterOperator setOperator,
                                                   FilterOperator fieldOperator,
                                                   String testPhrase)
        throws ParseException {

        try {
            switch (key) {
            case KEY_TAG_SHORT: // fallthrough
            case KEY_TAG_LONG:
                return createTagsPredicate(setOperator, fieldOperator, testPhrase);
            case KEY_ATTACHMENT_SHORT: // fallthrough
            case KEY_ATTACHMENT_LONG:
                return createAttachmentsPredicate(setOperator, fieldOperator, testPhrase);
            default:
                if (isValidKey(key)) {
                    throw new ParseException(String.format(MESSAGE_INVALID_KEY_DOUBLE_OPERATOR_FORMAT, key));
                } else {
                    throw new ParseException(String.format(MESSAGE_INVALID_KEY_FORMAT, key));
                }
            }
        } catch (InvalidPredicateOperatorException e) {
            throw new ParseException(MESSAGE_INVALID_OPERATOR_FORMAT, e);
        } catch (InvalidPredicateTestPhraseException e) {
            throw new ParseException(String.format(MESSAGE_INVALID_TESTPHRASE_FORMAT, testPhrase), e);
        }
    }

    /**
     * Checks if the given string represents a valid field identifier.
     *
     * @param key The field identifier to check.
     * @return True if the field identifier is valid, false otherwise.
     */
    private static boolean isValidKey(String key) {
        switch (key) {
        case KEY_NAME_SHORT:
        case KEY_NAME_LONG:
        case KEY_DEADLINE_SHORT:
        case KEY_DEADLINE_LONG:
        case KEY_PRIORITY_SHORT:
        case KEY_PRIORITY_LONG:
        case KEY_FREQUENCY_SHORT:
        case KEY_FREQUENCY_LONG:
        case KEY_TAG_SHORT:
        case KEY_TAG_LONG:
        case KEY_ATTACHMENT_SHORT:
        case KEY_ATTACHMENT_LONG:
            return true;
        default:
            return false;
        }
    }

    /**
     * Creates a predicate matches any textual or date field in a task, using the convenience operator.
     * It does not match numeric fields because they clutter the results and are usually not intended by the user.
     *
     * @param testPhrase The test phrase to compare with the specific field of each task.
     */
    private static Predicate<Task> createPredicateAny(String testPhrase) {
        return ALWAYS_FALSE
            .or(silencePredicateException(() -> createNamePredicate(FilterOperator.CONVENIENCE, testPhrase)))
            .or(silencePredicateException(() -> createDeadlinePredicate(FilterOperator.CONVENIENCE, testPhrase)))
            .or(silencePredicateException(() -> createTagsPredicate(
                FilterOperator.CONVENIENCE, FilterOperator.CONVENIENCE, testPhrase)))
            .or(silencePredicateException(() -> createAttachmentsPredicate(
                FilterOperator.CONVENIENCE, FilterOperator.CONVENIENCE, testPhrase)));
    }


    /**
     * Parses the given {@code String} of arguments in the context of the FilterCommand and returns an
     * FilterCommand object for execution.
     *
     * @param args The filter expression to parse.
     * @throws RichParseException if the user input does not conform the expected format.
     */
    @Override
    public FilterCommand parse(String args) throws RichParseException {
        String trimmedArgs = args.trim();
        if (trimmedArgs.isEmpty()) {
            throw new RichParseException(
                String.format(MESSAGE_INVALID_COMMAND_FORMAT, FilterCommand.MESSAGE_USAGE), TEXT_STYLE_CLASS_DEFAULT);
        }

        try {
            BooleanExpressionParser<Task> expressionParser =
                new BooleanExpressionParser<>((tokenizer, reservedCharPredicate) -> {
                    final Predicate<Character> effectiveUnquotedCharacterPredicate = reservedCharPredicate.negate()
                        .and(ALLOWED_UNQUOTED_CHARACTER_PREDICATE);

                    return tmpCreateFilterUnit(tokenizer, effectiveUnquotedCharacterPredicate,
                        ALLOWED_KEY_CHARACTER_PREDICATE);
                });
            Predicate<Task> predicate = expressionParser.parse(trimmedArgs);

            return new FilterCommand(predicate);

        } catch (IllegalArgumentException e) {
            throw new RichParseException("Invalid filter expression: " + e.getMessage(), TEXT_STYLE_CLASS_DEFAULT);
        } catch (TokenizationMissingEndQuoteException e) {
            throw createRichParseException(trimmedArgs, e, "Matching end quote is missing!");
        } catch (TokenizationUnexpectedQuoteException e) {
            throw createRichParseException(trimmedArgs, e, "Unexpected quote in textual keyword!");
        } catch (TokenizationNoMatchableCharacterException e) {
            throw createRichParseException(trimmedArgs, e, "Unexpected character!");
        } catch (TokenizationEndOfStringException | BooleanExpressionUnexpectedEndOfStringException e) {
            throw createRichParseException(trimmedArgs,
                new TokenizationMismatchException(trimmedArgs.length(), trimmedArgs.length(),
                    "Unexpected end of tokenizer string"),
                "Unexpected end of filter expression!");
        } catch (BooleanExpressionInvalidOperatorException e) {
            throw createRichParseException(trimmedArgs, e, "Unknown operator!");
        } catch (BooleanExpressionMismatchedLeftBracketException e) {
            throw createRichParseException(trimmedArgs, e, "Expected a right bracket to match an existing left bracket!");
        } catch (BooleanExpressionMismatchedRightBracketException e) {
            throw createRichParseException(trimmedArgs, e, "Mismatched right bracket!");
        } catch (BooleanExpressionUnexpectedBinaryOperatorException e) {
            throw createRichParseException(trimmedArgs, e, "Logical operator not expected in this context!");
        } catch (BooleanExpressionUnexpectedRightBracketException e) {
            throw createRichParseException(trimmedArgs, e, "Unexpected right bracket after an operator!");
        } catch (TokenizationException e) {
            throw createDefaultRichParseException(trimmedArgs, "Invalid filter expression!");
        }
    }

    /**
     * Constructs a RichParseException that does not highlight any substring.
     */
    private RichParseException createDefaultRichParseException(String trimmedArgs, String message) {
        return new RichParseException(FilterCommand.COMMAND_WORD + ' ' + trimmedArgs + '\n' + message,
            TEXT_STYLE_CLASS_DEFAULT);
    }

    /**
     * Constructs a RichParseException from the error substring denoted by the TokenMismatchException.
     */
    private RichParseException createRichParseException(String input, TokenizationMismatchException e,
                                                        String message) {
        List<ResultDisplay.StyledText> parts = new ArrayList<>();
        parts.add(new ResultDisplay.StyledText(FilterCommand.COMMAND_WORD + ' ', TEXT_STYLE_CLASS_DEFAULT));
        if (e.getBeginIndex() > 0) {
            parts.add(new ResultDisplay.StyledText(input.substring(0, e.getBeginIndex()), TEXT_STYLE_CLASS_DEFAULT));
        }
        int effectiveEndIndex = Math.min(Math.max(e.getEndIndex(), e.getBeginIndex() + 1), input.length());
        if (e.getBeginIndex() < effectiveEndIndex) {
            parts.add(new ResultDisplay.StyledText(input.substring(e.getBeginIndex(), effectiveEndIndex),
                TEXT_STYLE_CLASS_ERROR));
        } else {
            parts.add(new ResultDisplay.StyledText("......", TEXT_STYLE_CLASS_ERROR));
        }
        if (effectiveEndIndex < input.length()) {
            parts.add(new ResultDisplay.StyledText(input.substring(effectiveEndIndex), TEXT_STYLE_CLASS_DEFAULT));
        }
        parts.add(new ResultDisplay.StyledText("\n" + message, TEXT_STYLE_CLASS_DEFAULT));
        return new RichParseException(parts);
    }


    private static Predicate<Task> tmpCreateFilterUnit(StringTokenizer tokenizer,
                                                    Predicate<Character> effectiveUnquotedCharacterPredicate,
                                                    Predicate<Character> allowedKeyCharacterPredicate)
        throws TokenizationMissingEndQuoteException, TokenizationUnexpectedQuoteException,
        TokenizationNoMatchableCharacterException, TokenizationEndOfStringException, RichParseException {

        try {
            return createFilterUnit(tokenizer, effectiveUnquotedCharacterPredicate, allowedKeyCharacterPredicate);
        } catch (ParseException e) {
            throw new RichParseException(e.getMessage(), TEXT_STYLE_CLASS_DEFAULT);
        }
    }

    /**
     * Reads and returns a predicate from the given string tokenizer.
     *
     * @param tokenizer                           The string tokenizer.
     * @param effectiveUnquotedCharacterPredicate A predicate that specifies the allowable characters
     *                                            for unquoted test phrases.
     * @param allowedKeyCharacterPredicate        A predicate that specifies the allowable characters
     *                                            for filter field identifiers (i.e. keys).
     *
     * @throws ParseException if the user input does not conform the expected format.
     */
    private static Predicate<Task> createFilterUnit(StringTokenizer tokenizer,
                                                    Predicate<Character> effectiveUnquotedCharacterPredicate,
                                                    Predicate<Character> allowedKeyCharacterPredicate)
        throws TokenizationMissingEndQuoteException, TokenizationUnexpectedQuoteException,
        TokenizationNoMatchableCharacterException, TokenizationEndOfStringException, ParseException {

        int tokenizerLocation = tokenizer.getLocation(); // get the location in case we need to rewind
        final String key = tokenizer.tryNextString(allowedKeyCharacterPredicate);
        String opString;
        if (key != null && tokenizer.hasNextToken()
            && (opString = tokenizer.tryNextPattern(FILTER_OPERATOR_PATTERN)) != null) {
            final FilterOperator operator = FilterOperator.parse(opString);
            String opString2 = tokenizer.tryNextPattern(FILTER_OPERATOR_PATTERN); // could be null
            final String testPhrase = tokenizer.nextString(effectiveUnquotedCharacterPredicate);
            if (opString2 == null) {
                // has only one filter operator
                return createPredicate(key, operator, testPhrase);
            } else {
                // has two filter operators
                final FilterOperator operator2 = FilterOperator.parse(opString2);
                return createPredicate(key, operator, operator2, testPhrase);
            }
        } else {
            tokenizer.setLocation(tokenizerLocation); // rewind the tokenizer
            final String testPhrase = tokenizer.nextString(effectiveUnquotedCharacterPredicate);
            return createPredicateAny(testPhrase);
        }
    }

}
