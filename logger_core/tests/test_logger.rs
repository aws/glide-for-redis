/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use test_env_helpers::*;

#[cfg(test)]
#[after_all]
#[before_all]
mod tests {
    use logger_core::{init, log_debug, log_trace};
    use rand::{distributions::Alphanumeric, Rng};
    use std::fs::{read_dir, read_to_string, remove_dir_all};
    const FILE_DIRECTORY: &str = "glide-logs";

    fn generate_random_string(length: usize) -> String {
        rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(length)
            .map(char::from)
            .collect()
    }

    fn get_file_contents(file_name: &str) -> String {
        let files = read_dir(FILE_DIRECTORY).unwrap();
        let file = files
            .into_iter()
            .find(|path| {
                path.as_ref()
                    .unwrap()
                    .path()
                    .file_name()
                    .unwrap()
                    .to_str()
                    .unwrap()
                    .starts_with(file_name)
            })
            .unwrap();
        read_to_string(file.unwrap().path()).unwrap()
    }

    #[test]
    fn log_to_console_works_after_multiple_inits_diff_log_level() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), None);
        init(Some(logger_core::Level::Debug), None);
        // you should see in the console something like '2023-07-07T06:57:54.446236Z DEBUG logger_core: e49NaJ5J41 - foo'
        log_debug(identifier.clone(), "foo");
        // make sure that something like '2023-07-07T06:57:54.446236Z DEBUG logger_core: e49NaJ5J41 - boo' does not appear
        log_trace(identifier, "boo");
    }

    #[test]
    fn log_to_file_works_after_multiple_inits() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), Some(identifier.as_str()));
        init(Some(logger_core::Level::Debug), Some(identifier.as_str()));
        log_debug(identifier.clone(), "foo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contents: {}",
            contents
        );
        assert!(contents.contains("foo"), "Contents: {}", contents);
    }

    #[test]
    fn log_to_file_works_after_console_init() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), None);
        init(Some(logger_core::Level::Trace), Some(identifier.as_str()));
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Debug), Some(identifier.as_str()));
        log_debug(identifier.clone(), "foo");
        log_trace(identifier.clone(), "boo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contents: {}",
            contents
        );
        assert!(contents.contains("foo"), "Contents: {}", contents);
        assert!(!contents.contains("boo"));
    }

    #[test]
    fn log_to_file_disabled_when_console_init() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), Some(identifier.as_str()));
        init(Some(logger_core::Level::Trace), None);
        // you should see in the console something like '2023-07-07T06:57:54.446236Z TRACE logger_core: e49NaJ5J41 - foo'
        log_trace(identifier.clone(), "foo");
        let contents = get_file_contents(identifier.as_str());
        assert!(contents.is_empty());
    }

    fn clean() -> Result<(), std::io::Error> {
        remove_dir_all(FILE_DIRECTORY)
    }

    fn after_all() {
        clean().expect("Cannot remove log directory");
    }

    fn before_all() {
        let _ = clean();
    }
}
