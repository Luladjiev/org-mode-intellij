;;; check-edit-fixtures.el --- Compare the edit fixtures with Emacs  -*- lexical-binding: t; -*-

;; Usage, from the repository root:
;;
;;   emacs --batch -l scripts/check-edit-fixtures.el
;;
;; For every test-fixtures/org/edit/OPERATION/NAME.org this runs the matching Emacs command at each `<caret>' (once
;; per Heading or checkbox line, as the plugin does with several carets) and compares the text with
;; NAME.expected.org, ignoring caret markers. Cases listed in `check-edit-fixtures-known' are the differences
;; documented in docs/compatibility.md and must still differ; anything else that differs fails. Exits non-zero on
;; failure. CI does not run it (no Emacs there).

;;; Code:

(require 'org)
(require 'org-list)

(defconst check-edit-fixtures-root
  (expand-file-name ".." (file-name-directory (or load-file-name buffer-file-name))))

(defconst check-edit-fixtures-commands
  '(("cycle-todo" org-todo heading)
    ("promote-heading" org-do-promote heading)
    ("demote-heading" org-do-demote heading)
    ("promote-subtree" org-promote-subtree heading)
    ("demote-subtree" org-demote-subtree heading)
    ("move-subtree-up" org-move-subtree-up heading)
    ("move-subtree-down" org-move-subtree-down heading)
    ("toggle-checkbox" org-toggle-checkbox line)
    ("cycle-level" org-cycle-level heading))
  "OPERATION directory, Emacs command, and what several carets are deduplicated by.")

(defconst check-edit-fixtures-known
  '("cycle-todo/todo-to-done"          ; Emacs normalizes `*   TODO   x' to single spaces
    "promote-heading/from-body"        ; Emacs promotes only from the Heading line
    "toggle-checkbox/nested"           ; Emacs also checks the parent once all its children are checked
    "toggle-checkbox/states")          ; Emacs turns lowercase `[x]' into `[X]', the plugin unchecks it
  "OPERATION/NAME of fixtures that deliberately differ from Emacs, each with its docs/compatibility.md row.")

(defun check-edit-fixtures--strip (text)
  (replace-regexp-in-string "<caret>" "" text t t))

(defun check-edit-fixtures--targets (dedupe)
  "Markers at the `<caret>' positions, removed from the buffer, one per Heading or line per DEDUPE."
  (let (targets seen)
    (goto-char (point-min))
    (while (search-forward "<caret>" nil t)
      (replace-match "" t t)
      (let ((key (save-excursion
                   (if (eq dedupe 'heading)
                       (progn (org-back-to-heading t) (point))
                     (line-beginning-position)))))
        (unless (member key seen)
          (push key seen)
          (push (copy-marker (point)) targets))))
    ;; Last first, so earlier positions stay valid even if an edit moves text.
    (sort targets (lambda (a b) (> a b)))))

(defun check-edit-fixtures--run (file command dedupe)
  "FILE's text after COMMAND at each of its carets."
  (with-temp-buffer
    (insert-file-contents file)
    (org-mode)
    (dolist (target (check-edit-fixtures--targets dedupe))
      (goto-char target)
      ;; In batch the mark is active from the start of the buffer, which would make commands act on that region.
      (deactivate-mark t)
      (call-interactively command))
    (buffer-substring-no-properties (point-min) (point-max))))

(defun check-edit-fixtures--file-string (file)
  (with-temp-buffer (insert-file-contents file) (buffer-string)))

(let ((org-todo-keywords '((sequence "TODO" "DONE")))
      (org-auto-align-tags nil)
      (org-log-done nil)
      (org-todo-log-states nil)
      (org-use-fast-todo-selection nil)
      (org-loop-over-headlines-in-active-region nil)
      (tab-width 8)
      (failures 0))
  (dolist (spec check-edit-fixtures-commands)
    (let ((dir (expand-file-name (concat "test-fixtures/org/edit/" (car spec)) check-edit-fixtures-root)))
      (when (file-directory-p dir)
        (dolist (input (directory-files dir t "\\.org\\'"))
          (unless (string-suffix-p ".expected.org" input)
            (let* ((name (concat (car spec) "/" (file-name-base input)))
                   (expected (check-edit-fixtures--strip
                              (check-edit-fixtures--file-string
                               (concat (file-name-sans-extension input) ".expected.org"))))
                   (actual (condition-case err
                               (check-edit-fixtures--run input (nth 1 spec) (nth 2 spec))
                             (error (format "ERROR: %S" err))))
                   (same (string= expected actual))
                   (known (member name check-edit-fixtures-known)))
              (cond
               ((and same (not known)) (message "ok       %s" name))
               ((and (not same) known) (message "known    %s (docs/compatibility.md)" name))
               (t
                (setq failures (1+ failures))
                (message "%s %s\n--- plugin (expected.org)\n%s\n--- Emacs %s\n%s"
                         (if known "FIXED   " "DIFF    ") name expected (org-version) actual)))))))))
  (message "%d failure(s)" failures)
  (kill-emacs (if (zerop failures) 0 1)))

;;; check-edit-fixtures.el ends here
