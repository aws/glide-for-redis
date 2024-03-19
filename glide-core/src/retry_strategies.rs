/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
use crate::client::ConnectionRetryStrategy;
use std::time::Duration;
use tokio_retry::strategy::{jitter, ExponentialBackoff};

#[derive(Clone, Debug)]
pub(super) struct RetryStrategy {
    factor: u32,
    exponent_base: u32,
    number_of_retries: u32,
}

impl RetryStrategy {
    pub(super) fn new(data: Option<ConnectionRetryStrategy>) -> Self {
        match data {
            Some(ref strategy) => get_exponential_backoff(
                strategy.exponent_base,
                strategy.factor,
                strategy.number_of_retries,
            ),
            None => get_exponential_backoff(EXPONENT_BASE, FACTOR, NUMBER_OF_RETRIES),
        }
    }

    pub(super) fn get_iterator(&self) -> impl Iterator<Item = Duration> {
        ExponentialBackoff::from_millis(self.exponent_base as u64)
            .factor(self.factor as u64)
            .map(jitter)
            .take(self.number_of_retries as usize)
    }
}

pub(crate) const EXPONENT_BASE: u32 = 2;
pub(crate) const FACTOR: u32 = 100;
pub(crate) const NUMBER_OF_RETRIES: u32 = 5;

pub(crate) fn get_exponential_backoff(
    exponent_base: u32,
    factor: u32,
    number_of_retries: u32,
) -> RetryStrategy {
    let exponent_base = if exponent_base > 0 {
        exponent_base
    } else {
        EXPONENT_BASE
    };
    let factor = if factor > 0 { factor } else { FACTOR };

    RetryStrategy {
        factor,
        exponent_base,
        number_of_retries,
    }
}

#[cfg(feature = "socket-layer")]
pub(crate) fn get_fixed_interval_backoff(
    fixed_interval: u32,
    number_of_retries: u32,
) -> RetryStrategy {
    get_exponential_backoff(1, fixed_interval, number_of_retries)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(feature = "socket-layer")]
    #[test]
    fn test_fixed_intervals_with_jitter() {
        let retries = 3;
        let interval_duration = 10;
        let intervals = get_fixed_interval_backoff(interval_duration, retries).get_iterator();

        let mut counter = 0;
        for duration in intervals {
            counter += 1;
            assert!(duration.as_millis() <= interval_duration as u128);
        }
        assert_eq!(counter, retries);
    }

    #[test]
    fn test_exponential_backoff_with_jitter() {
        let retries = 3;
        let base = 10;
        let factor = 5;
        let intervals = get_exponential_backoff(base, factor, retries).get_iterator();

        let mut counter = 0;
        for duration in intervals {
            counter += 1;
            let unjittered_duration = factor * (base.pow(counter));
            assert!(duration.as_millis() <= unjittered_duration as u128);
        }

        assert_eq!(counter, retries);
    }
}
