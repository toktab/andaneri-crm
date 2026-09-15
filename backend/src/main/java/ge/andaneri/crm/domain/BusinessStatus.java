package ge.andaneri.crm.domain;

/** Where a business stands in the pipeline. "No answer" is a call result, not a stage, so it is not here. */
public enum BusinessStatus {
    NEW, CONTACTED, INTERESTED, MEETING, TESTING, NEGOTIATION, CUSTOMER, REPEAT_CUSTOMER, FOLLOW_UP_LATER, NOT_INTERESTED, LOST
}
