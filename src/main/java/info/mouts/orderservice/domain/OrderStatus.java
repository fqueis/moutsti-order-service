package info.mouts.orderservice.domain;

/**
 * Represents the current status of an order in the system.
 * This enum tracks the lifecycle of an order from receipt to completion or
 * failure.
 */
public enum OrderStatus {
    /**
     * Initial state when an order is received from System A.
     * The order is waiting to be processed.
     */
    RECEIVED, // Order received from System A, waiting for processing

    /**
     * Order is currently being processed.
     * This includes activities such as calculating order total and validating
     * items.
     */
    PROCESSING, // Order being processed (eg: calculating order total, validating items, etc)

    /**
     * Order has been successfully processed.
     * All validations and calculations have been completed successfully.
     */
    PROCESSED, // Order successfully processed

    /**
     * Order processing has failed.
     * This can occur either before being sent to DLQ or after retry attempts.
     */
    FAILED, // Failed during processing (before going to DLQ or after retries)

    /**
     * Order has been cancelled after a failure.
     * This is the final state for failed orders.
     */
    CANCELLED // Order cancelled after failure

}
