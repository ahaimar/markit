"use client";

import { useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth";
import { getApiClient } from "@/lib/api/factory";
import { useApi } from "@/lib/useApi";
import { Button } from "@/components/ui/Button";
import { Card, EmptyState } from "@/components/ui/Card";
import { Input, Select, Textarea } from "@/components/ui/Field";
import { TaskPriorityBadge, TaskStatusBadge } from "@/components/ui/Badge";
import type { Task, TaskStatus } from "@/lib/types";
import { ApiFailure } from "@/lib/types";

type StatusFilter = "ALL" | TaskStatus;

const STATUS_OPTIONS: TaskStatus[] = ["TODO", "IN_PROGRESS", "DONE"];
const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "TODO", label: "To do" },
  { value: "IN_PROGRESS", label: "In progress" },
  { value: "DONE", label: "Done" },
];

interface TaskFormState {
  title: string;
  description: string;
  status: TaskStatus;
  priority: "LOW" | "MEDIUM" | "HIGH";
  dueDate: string;
}

const EMPTY_FORM: TaskFormState = {
  title: "",
  description: "",
  status: "TODO",
  priority: "MEDIUM",
  dueDate: "",
};

function toForm(task: Task | null): TaskFormState {
  if (!task) return EMPTY_FORM;
  return {
    title: task.title,
    description: task.description ?? "",
    status: task.status,
    priority: task.priority,
    dueDate: task.dueDate ? task.dueDate.slice(0, 10) : "",
  };
}

export default function TasksPage() {
  return (
    <RequireAuth>
      <TasksContent />
    </RequireAuth>
  );
}

function TasksContent() {
  const { requireUserId } = useAuth();
  const userId = requireUserId();

  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [editing, setEditing] = useState<Task | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<TaskFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const { data, loading, error, reload } = useApi(
    () => getApiClient().listTasks(userId, filter === "ALL" ? {} : { status: filter }),
    [userId, filter],
  );

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setFormOpen(true);
  }

  function openEdit(task: Task) {
    setEditing(task);
    setForm(toForm(task));
    setFormError(null);
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
    setFormError(null);
  }

  async function submit() {
    if (!form.title.trim()) {
      setFormError("Title is required.");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const dueDate = form.dueDate ? new Date(`${form.dueDate}T12:00:00`).toISOString() : undefined;
      if (editing) {
        await getApiClient().updateTask(userId, editing.taskId, {
          title: form.title.trim(),
          description: form.description.trim() || undefined,
          status: form.status,
          priority: form.priority,
          dueDate,
        });
      } else {
        await getApiClient().createTask(userId, {
          title: form.title.trim(),
          description: form.description.trim() || undefined,
          status: form.status,
          priority: form.priority,
          dueDate,
        });
      }
      closeForm();
      reload();
    } catch (err) {
      setFormError(err instanceof ApiFailure ? err.message : "Something went wrong.");
    } finally {
      setSubmitting(false);
    }
  }

  async function changeStatus(task: Task, status: TaskStatus) {
    try {
      await getApiClient().updateTask(userId, task.taskId, { status });
      reload();
    } catch (err) {
      alert(err instanceof ApiFailure ? err.message : "Something went wrong.");
    }
  }

  async function remove(task: Task) {
    if (!confirm(`Delete "${task.title}"?`)) return;
    try {
      await getApiClient().deleteTask(userId, task.taskId);
      if (editing?.taskId === task.taskId) closeForm();
      reload();
    } catch (err) {
      alert(err instanceof ApiFailure ? err.message : "Something went wrong.");
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">Your tasks</h1>
        <Button onClick={openCreate}>New task</Button>
      </div>

      {(formOpen || editing) && (
        <Card className="flex flex-col gap-4">
          <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-50">
            {editing ? "Edit task" : "New task"}
          </h2>
          <Input
            label="Title"
            name="title"
            placeholder="What needs doing?"
            value={form.title}
            maxLength={200}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          <Textarea
            label="Description"
            name="description"
            placeholder="Add some detail (optional)"
            value={form.description}
            maxLength={5000}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <div className="grid gap-4 sm:grid-cols-3">
            <Select
              label="Status"
              name="status"
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as TaskStatus })}
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s === "IN_PROGRESS" ? "In progress" : s[0] + s.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
            <Select
              label="Priority"
              name="priority"
              value={form.priority}
              onChange={(e) => setForm({ ...form, priority: e.target.value as TaskFormState["priority"] })}
            >
              {(["LOW", "MEDIUM", "HIGH"] as const).map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </Select>
            <Input
              label="Due date"
              name="dueDate"
              type="date"
              value={form.dueDate}
              onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
            />
          </div>
          {formError && <p className="text-sm text-red-600 dark:text-red-400">{formError}</p>}
          <div className="flex items-center gap-3">
            <Button onClick={() => void submit()} loading={submitting}>
              {editing ? "Save changes" : "Create task"}
            </Button>
            <Button variant="ghost" onClick={closeForm} disabled={submitting}>
              Cancel
            </Button>
          </div>
        </Card>
      )}

      <div className="flex flex-wrap items-center gap-2">
        {STATUS_FILTERS.map((option) => (
          <button
            key={option.value}
            onClick={() => setFilter(option.value)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
              filter === option.value
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "bg-zinc-100 text-zinc-600 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700"
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      {loading && <div className="h-40 animate-pulse rounded-xl bg-zinc-100 dark:bg-zinc-800" />}

      {!loading && error && <EmptyState title="Could not load tasks" hint={error.message} />}

      {!loading && data && data.items.length === 0 && (
        <EmptyState
          title={filter === "ALL" ? "No tasks yet" : `No ${filter.toLowerCase().replace("_", " ")} tasks`}
          hint="Create a task to start tracking what's on your plate."
        >
          <Button onClick={openCreate} className="mt-4">
            New task
          </Button>
        </EmptyState>
      )}

      {!loading && data && data.items.length > 0 && (
        <div className="flex flex-col gap-3">
          {data.items.map((task) => (
            <Card key={task.taskId} className="flex items-center justify-between gap-4">
              <div className="flex min-w-0 flex-col gap-1.5">
                <div className="flex items-center gap-3">
                  <Select
                    aria-label="Status"
                    value={task.status}
                    className="w-32 px-2 py-1 text-xs"
                    onChange={(e) => void changeStatus(task, e.target.value as TaskStatus)}
                  >
                    {STATUS_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {s === "IN_PROGRESS" ? "In progress" : s[0] + s.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </Select>
                  <span
                    className={`truncate font-medium text-zinc-900 dark:text-zinc-50 ${
                      task.status === "DONE" ? "line-through text-zinc-400 dark:text-zinc-500" : ""
                    }`}
                  >
                    {task.title}
                  </span>
                </div>
                {task.description && (
                  <p className="truncate text-sm text-zinc-500 dark:text-zinc-400">{task.description}</p>
                )}
                <div className="flex flex-wrap items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400">
                  <TaskStatusBadge status={task.status} />
                  <TaskPriorityBadge priority={task.priority} />
                  {task.dueDate && <span>Due {new Date(task.dueDate).toLocaleDateString()}</span>}
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Button variant="secondary" className="px-3 py-1.5" onClick={() => openEdit(task)}>
                  Edit
                </Button>
                <Button
                  variant="ghost"
                  className="px-3 py-1.5 text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40"
                  onClick={() => void remove(task)}
                >
                  Delete
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}